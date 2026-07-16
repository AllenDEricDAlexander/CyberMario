ALTER TABLE nutrition_family
    ADD COLUMN owner_member_profile_id BIGINT;

UPDATE nutrition_family
SET owner_member_profile_id = (
    SELECT MIN(binding.scope_id)
    FROM nutrition_scoped_role_binding binding
    JOIN nutrition_member_profile member_profile ON member_profile.id = binding.scope_id
    WHERE binding.subject_type = 'USER'
      AND binding.subject_id = nutrition_family.owner_user_id
      AND binding.role_code = 'PROFILE_OWNER'
      AND binding.scope_type = 'MEMBER_PROFILE'
      AND member_profile.family_id = nutrition_family.id
)
WHERE owner_member_profile_id IS NULL;

UPDATE nutrition_family
SET owner_member_profile_id = (
    SELECT MIN(member_profile.id)
    FROM nutrition_member_profile member_profile
    WHERE member_profile.family_id = nutrition_family.id
      AND member_profile.bound_user_id = nutrition_family.owner_user_id
)
WHERE owner_member_profile_id IS NULL;

UPDATE nutrition_member_profile
SET bound_user_id = (
        SELECT family.owner_user_id
        FROM nutrition_family family
        WHERE family.owner_member_profile_id = nutrition_member_profile.id
    ),
    nickname = COALESCE((
        SELECT owner_user.username
        FROM nutrition_family family
        JOIN sys_user owner_user ON owner_user.id = family.owner_user_id
        WHERE family.owner_member_profile_id = nutrition_member_profile.id
          AND owner_user.deleted = FALSE
    ), nickname),
    login_enabled = TRUE,
    status = 'ACTIVE',
    deleted = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE id IN (
    SELECT owner_member_profile_id
    FROM nutrition_family
    WHERE owner_member_profile_id IS NOT NULL
);

UPDATE nutrition_scoped_role_binding
SET status = 'ACTIVE',
    deleted = FALSE,
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE subject_type = 'USER'
  AND role_code = 'PROFILE_OWNER'
  AND scope_type = 'MEMBER_PROFILE'
  AND EXISTS (
      SELECT 1
      FROM nutrition_family family
      WHERE family.owner_user_id = nutrition_scoped_role_binding.subject_id
        AND family.owner_member_profile_id = nutrition_scoped_role_binding.scope_id
  );

CREATE INDEX idx_nutrition_family_owner_member_profile
    ON nutrition_family (owner_member_profile_id);
