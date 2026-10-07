package app.idl.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.idl.domain.avatar.AssetPacks
import app.idl.domain.avatar.AssetRegistry
import app.idl.domain.avatar.StoredAvatar
import kotlinx.coroutines.flow.Flow

/*
 * Local cache. Complex value objects (avatar config, presence state/view) are stored as
 * JSON blobs encoded with IdlJson: they are always read and written whole, and blobs keep
 * schema migrations trivial while the domain model is still moving.
 */

@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey val id: Int = 0,
    val userId: String,
    val username: String,
    val displayName: String,
    val invisible: Boolean,
)

@Entity(tableName = "avatars")
data class AvatarEntity(
    @PrimaryKey val userId: String,
    val json: String,
)

@Entity(tableName = "own_presence")
data class OwnPresenceEntity(
    @PrimaryKey val source: String,
    val json: String,
    val pendingSync: Boolean,
)

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val userId: String,
    val username: String,
    val displayName: String,
    val status: String,
    val isCloseFriend: Boolean,
)

@Entity(tableName = "friend_presence")
data class FriendPresenceEntity(
    @PrimaryKey val userId: String,
    val json: String,
    val fetchedAtMs: Long,
)

@Entity(tableName = "reactions")
data class ReactionEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val recipientId: String,
    val template: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val dismissedAtMs: Long?,
)

@Entity(tableName = "privacy_rules")
data class PrivacyRulesEntity(
    @PrimaryKey val id: Int = 0,
    val json: String,
)

@Entity(tableName = "widget_subscriptions")
data class WidgetSubscriptionEntity(
    @PrimaryKey val appWidgetId: Int,
    val kind: String,
    val friendUserId: String?,
)

@Entity(tableName = "user_economy")
data class UserEconomyStateEntity(
    @PrimaryKey val userId: String,
    val currentCharge: Long,
    val lastEvaluatedEpochMs: Long,
    val lifetimeChargeEarned: Long,
)

@Entity(tableName = "friend_tethers")
data class FriendTetherEntity(
    @PrimaryKey val friendUserId: String,
    val hasLocalWidgetInstalled: Boolean,
    val hasRemoteWidgetInstalled: Boolean,
    val tetherActivatedEpochMs: Long?,
) {
    val isMutuallyTethered: Boolean
        get() = hasLocalWidgetInstalled && hasRemoteWidgetInstalled
}

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 0,
    val lastSuccessMs: Long?,
    val lastAttemptMs: Long?,
    val lastError: String?,
)

@Dao
interface IdlDao {
    // Session
    @Query("SELECT * FROM session WHERE id = 0") fun session(): Flow<SessionEntity?>
    @Query("SELECT * FROM session WHERE id = 0") suspend fun sessionNow(): SessionEntity?
    @Upsert suspend fun upsertSession(s: SessionEntity)

    // Avatars
    @Query("SELECT * FROM avatars WHERE userId = :userId") fun avatar(userId: String): Flow<AvatarEntity?>
    @Query("SELECT * FROM avatars WHERE userId = :userId") suspend fun avatarNow(userId: String): AvatarEntity?
    @Upsert suspend fun upsertAvatar(a: AvatarEntity)

    // Own presence
    @Query("SELECT * FROM own_presence") fun ownPresence(): Flow<List<OwnPresenceEntity>>
    @Query("SELECT * FROM own_presence") suspend fun ownPresenceNow(): List<OwnPresenceEntity>
    @Upsert suspend fun upsertOwnPresence(p: OwnPresenceEntity)
    @Query("DELETE FROM own_presence WHERE source = :source") suspend fun deleteOwnPresence(source: String)

    // Friends
    @Query("SELECT * FROM friends ORDER BY displayName COLLATE NOCASE") fun friends(): Flow<List<FriendEntity>>
    @Query("SELECT * FROM friends ORDER BY displayName COLLATE NOCASE") suspend fun friendsNow(): List<FriendEntity>
    @Query("SELECT * FROM friends WHERE userId = :userId") fun friend(userId: String): Flow<FriendEntity?>
    @Query("SELECT * FROM friends WHERE userId = :userId") suspend fun friendNow(userId: String): FriendEntity?
    @Query("DELETE FROM friends") suspend fun clearFriends()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFriends(f: List<FriendEntity>)
    @Upsert suspend fun upsertFriend(f: FriendEntity)

    @Transaction
    suspend fun replaceFriends(f: List<FriendEntity>) {
        clearFriends()
        insertFriends(f)
    }

    // Friend presence
    @Query("SELECT * FROM friend_presence") fun friendPresence(): Flow<List<FriendPresenceEntity>>
    @Query("SELECT * FROM friend_presence WHERE userId = :userId") fun friendPresence(userId: String): Flow<FriendPresenceEntity?>
    @Query("SELECT * FROM friend_presence WHERE userId = :userId") suspend fun friendPresenceNow(userId: String): FriendPresenceEntity?
    @Query("SELECT * FROM friend_presence") suspend fun friendPresenceAllNow(): List<FriendPresenceEntity>
    @Upsert suspend fun upsertFriendPresence(p: List<FriendPresenceEntity>)

    /** Revocation: remove everything cached about a user. */
    @Transaction
    suspend fun purgeUser(userId: String) {
        deleteFriend(userId)
        deleteFriendPresence(userId)
        deleteAvatar(userId)
        deleteTether(userId)
    }
    @Query("DELETE FROM friends WHERE userId = :userId") suspend fun deleteFriend(userId: String)
    @Query("DELETE FROM friend_presence WHERE userId = :userId") suspend fun deleteFriendPresence(userId: String)
    @Query("DELETE FROM avatars WHERE userId = :userId") suspend fun deleteAvatar(userId: String)

    // Reactions
    @Query("SELECT * FROM reactions ORDER BY createdAtMs DESC") fun reactions(): Flow<List<ReactionEntity>>
    @Query("SELECT * FROM reactions WHERE id = :id") suspend fun reactionNow(id: String): ReactionEntity?
    @Upsert suspend fun upsertReactions(r: List<ReactionEntity>)
    @Query("UPDATE reactions SET dismissedAtMs = :atMs WHERE id = :id") suspend fun dismissReaction(id: String, atMs: Long)
    @Query("DELETE FROM reactions WHERE expiresAtMs <= :nowMs") suspend fun purgeExpiredReactions(nowMs: Long)

    // Privacy
    @Query("SELECT * FROM privacy_rules WHERE id = 0") fun privacyRules(): Flow<PrivacyRulesEntity?>
    @Upsert suspend fun upsertPrivacyRules(r: PrivacyRulesEntity)

    // Widgets
    @Query("SELECT * FROM widget_subscriptions WHERE appWidgetId = :id") suspend fun widgetSubscription(id: Int): WidgetSubscriptionEntity?
    @Query("SELECT * FROM widget_subscriptions WHERE friendUserId = :userId") suspend fun widgetSubscriptionsFor(userId: String): List<WidgetSubscriptionEntity>
    @Upsert suspend fun upsertWidgetSubscription(s: WidgetSubscriptionEntity)
    @Query("DELETE FROM widget_subscriptions WHERE appWidgetId = :id") suspend fun deleteWidgetSubscription(id: Int)
    @Query("SELECT * FROM widget_subscriptions") suspend fun widgetSubscriptionsNow(): List<WidgetSubscriptionEntity>

    // Charge
    @Query("SELECT * FROM user_economy WHERE userId = :userId") fun economy(userId: String): Flow<UserEconomyStateEntity?>
    @Query("SELECT * FROM user_economy WHERE userId = :userId") suspend fun economyNow(userId: String): UserEconomyStateEntity?
    @Upsert suspend fun upsertEconomy(e: UserEconomyStateEntity)

    @Query("SELECT * FROM friend_tethers") fun tethers(): Flow<List<FriendTetherEntity>>
    @Query("SELECT * FROM friend_tethers") suspend fun tethersNow(): List<FriendTetherEntity>
    @Query("SELECT * FROM friend_tethers WHERE friendUserId = :userId") suspend fun tetherNow(userId: String): FriendTetherEntity?
    @Upsert suspend fun upsertTether(t: FriendTetherEntity)
    @Query("DELETE FROM friend_tethers WHERE friendUserId = :userId") suspend fun deleteTether(userId: String)

    // Sync
    @Query("SELECT * FROM sync_state WHERE id = 0") fun syncState(): Flow<SyncStateEntity?>
    @Query("SELECT * FROM sync_state WHERE id = 0") suspend fun syncStateNow(): SyncStateEntity?
    @Upsert suspend fun upsertSyncState(s: SyncStateEntity)
}

@Database(
    entities = [
        SessionEntity::class, AvatarEntity::class, OwnPresenceEntity::class, FriendEntity::class,
        FriendPresenceEntity::class, ReactionEntity::class, PrivacyRulesEntity::class,
        WidgetSubscriptionEntity::class, SyncStateEntity::class,
        UserEconomyStateEntity::class, FriendTetherEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class IdlDatabase : RoomDatabase() {
    abstract fun dao(): IdlDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_economy` (`userId` TEXT NOT NULL, " +
                        "`currentCharge` INTEGER NOT NULL, `lastEvaluatedEpochMs` INTEGER NOT NULL, " +
                        "`lifetimeChargeEarned` INTEGER NOT NULL, PRIMARY KEY(`userId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `friend_tethers` (`friendUserId` TEXT NOT NULL, " +
                        "`hasLocalWidgetInstalled` INTEGER NOT NULL, `hasRemoteWidgetInstalled` INTEGER NOT NULL, " +
                        "`tetherActivatedEpochMs` INTEGER, PRIMARY KEY(`friendUserId`))",
                )
            }
        }

        /** Rewrites each stored avatar to schema 3 and `base_teardrop`. Tables stay as they are. */
        fun migration2To3(registry: AssetRegistry) = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.query("SELECT userId, json FROM avatars").use { cursor ->
                    val userCol = cursor.getColumnIndexOrThrow("userId")
                    val jsonCol = cursor.getColumnIndexOrThrow("json")
                    while (cursor.moveToNext()) {
                        val userId = cursor.getString(userCol)
                        val json = cursor.getString(jsonCol)
                        val rewritten = StoredAvatar.rewrite(json, registry)
                        if (rewritten != json) {
                            db.execSQL(
                                "UPDATE avatars SET json = ? WHERE userId = ?",
                                arrayOf<Any>(rewritten, userId),
                            )
                        }
                    }
                }
            }
        }

        fun create(context: Context): IdlDatabase {
            val registry = AssetPacks.registry { path ->
                context.assets.open(path).bufferedReader().use { it.readText() }
            }
            return Room.databaseBuilder(context, IdlDatabase::class.java, "idl.db")
                .addMigrations(MIGRATION_1_2, migration2To3(registry))
                .build()
        }

        fun inMemory(context: Context): IdlDatabase =
            Room.inMemoryDatabaseBuilder(context, IdlDatabase::class.java).build()
    }
}
