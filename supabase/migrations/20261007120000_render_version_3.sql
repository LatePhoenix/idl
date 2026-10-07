-- AP-9: default renderVersion matches AvatarConfiguration.RENDER_VERSION (3).
-- Identity envelopes without an explicit renderVersion must compare equal to the Kotlin default.

create or replace function idl_private.identity_envelope(p jsonb) returns jsonb language sql immutable as $$
  select jsonb_strip_nulls(p) || jsonb_build_object(
    'styleDna', coalesce(p -> 'styleDna', jsonb_build_object(
      'styleFamily', 'cozy',
      'expressionIntensity', 'medium',
      'sceneDetailPreference', 'low_detail',
      'motionPreference', 'subtle',
      'semanticVisualOverrides', '{}'::jsonb)),
    'renderVersion', coalesce((p ->> 'renderVersion')::int, 3),
    'schemaVersion', 3,
    'packId', coalesce(nullif(p ->> 'packId', ''), 'core_proto'),
    'packVersion', coalesce((p ->> 'packVersion')::int, 1),
    'familyId', coalesce(nullif(p ->> 'familyId', ''), 'teardrop_face'),
    'itemIds', coalesce(p -> 'itemIds', '{}'::jsonb),
    'colorOverrides', coalesce(p -> 'colorOverrides', '{}'::jsonb),
    'unlinkedSlots', coalesce(p -> 'unlinkedSlots', '[]'::jsonb),
    'itemTransforms', coalesce(p -> 'itemTransforms', '{}'::jsonb),
    'background', coalesce(p -> 'background', '{"mode":"scene"}'::jsonb),
    'signatureFeatureAssetIds', coalesce(p -> 'signatureFeatureAssetIds', '[]'::jsonb),
    'restingExpressionId', coalesce(p ->> 'restingExpressionId', 'neutral')
  )
$$;

create or replace function public.put_avatar(p_config jsonb) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_uid uuid := idl_private.require_uid();
  v_config jsonb;
  v_id text;
begin
  if jsonb_typeof(p_config) <> 'object'
     or coalesce((p_config ->> 'schemaVersion')::int, 0) <> 3
     or coalesce(p_config ->> 'baseAssetId', '') = ''
     or coalesce(p_config ->> 'paletteAssetId', '') = '' then
    perform idl_private.fail('PT400', 'Invalid avatar', 'config');
  end if;
  perform idl_private.assert_free_asset(p_config ->> 'baseAssetId', 'baseAssetId');
  perform idl_private.assert_free_asset(p_config ->> 'paletteAssetId', 'paletteAssetId');
  perform idl_private.assert_free_asset(p_config ->> 'eyeFamilyAssetId', 'eyeFamilyAssetId');
  perform idl_private.assert_free_asset(p_config ->> 'mouthFamilyAssetId', 'mouthFamilyAssetId');
  perform idl_private.assert_free_asset(p_config ->> 'signatureHeadAccessoryAssetId', 'signatureHeadAccessoryAssetId');
  perform idl_private.assert_free_asset(p_config ->> 'signatureFaceAccessoryAssetId', 'signatureFaceAccessoryAssetId');
  perform idl_private.assert_free_asset(p_config ->> 'signatureBodyAccessoryAssetId', 'signatureBodyAccessoryAssetId');
  perform idl_private.assert_free_asset(p_config ->> 'defaultPropAssetId', 'defaultPropAssetId');
  perform idl_private.assert_free_asset(p_config ->> 'defaultSceneAssetId', 'defaultSceneAssetId');
  perform idl_private.assert_free_asset(p_config ->> 'defaultFrameAssetId', 'defaultFrameAssetId');
  for v_id in select jsonb_array_elements_text(coalesce(p_config -> 'signatureFeatureAssetIds', '[]'::jsonb)) loop
    perform idl_private.assert_free_asset(v_id, 'signatureFeatureAssetIds');
  end loop;
  for v_id in
    select jsonb_array_elements_text(value)
    from jsonb_each(coalesce(p_config -> 'itemIds', '{}'::jsonb))
  loop
    perform idl_private.assert_free_asset(v_id, 'itemIds');
  end loop;

  v_config := idl_private.migrate_avatar_config(p_config);
  insert into avatar_configurations (user_id, config, render_version, updated_at)
  values (v_uid, v_config, coalesce((v_config ->> 'renderVersion')::int, 3), now())
  on conflict (user_id) do update set config = excluded.config, render_version = excluded.render_version, updated_at = now();
  perform idl_private.notify_friends(v_uid, jsonb_build_object('type', 'presence_changed', 'userId', v_uid));
  return v_config;
end $$;
