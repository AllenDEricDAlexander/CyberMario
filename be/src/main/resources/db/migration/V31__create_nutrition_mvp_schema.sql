-- V31: create the initial family AI nutrition MVP schema.

CREATE TABLE nutrition_clan (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_nutrition_clan_owner_name UNIQUE (owner_user_id, name)
);

CREATE INDEX idx_nutrition_clan_owner_status ON nutrition_clan (owner_user_id, status);

CREATE TABLE nutrition_family (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    region VARCHAR(128),
    currency VARCHAR(16) NOT NULL DEFAULT 'CNY',
    default_meal_types JSONB NOT NULL DEFAULT '[]',
    ai_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ai_generate_time TIME,
    health_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    budget_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_nutrition_family_owner_name UNIQUE (owner_user_id, name)
);

CREATE INDEX idx_nutrition_family_owner_status ON nutrition_family (owner_user_id, status);

CREATE TABLE nutrition_clan_family (
    id BIGSERIAL PRIMARY KEY,
    clan_id BIGINT NOT NULL,
    family_id BIGINT NOT NULL,
    relation_status VARCHAR(32) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_nutrition_clan_family UNIQUE (clan_id, family_id)
);

CREATE INDEX idx_nutrition_clan_family_clan ON nutrition_clan_family (clan_id, relation_status);
CREATE INDEX idx_nutrition_clan_family_family ON nutrition_clan_family (family_id, relation_status);

CREATE TABLE nutrition_member_profile (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    bound_user_id BIGINT,
    nickname VARCHAR(128) NOT NULL,
    gender VARCHAR(32),
    birth_date DATE,
    height_cm DECIMAL(8, 2),
    weight_kg DECIMAL(8, 2),
    member_type VARCHAR(32) NOT NULL,
    login_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    guardian_member_id BIGINT,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_member_profile_family ON nutrition_member_profile (family_id, status);
CREATE INDEX idx_nutrition_member_profile_bound_user ON nutrition_member_profile (bound_user_id);
CREATE INDEX idx_nutrition_member_profile_guardian ON nutrition_member_profile (guardian_member_id);

CREATE TABLE nutrition_health_profile (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    member_profile_id BIGINT NOT NULL,
    activity_level VARCHAR(32),
    diet_goals JSONB NOT NULL DEFAULT '[]',
    allergy_tags JSONB NOT NULL DEFAULT '[]',
    dislike_tags JSONB NOT NULL DEFAULT '[]',
    restriction_tags JSONB NOT NULL DEFAULT '[]',
    target_calories DECIMAL(10, 2),
    target_protein DECIMAL(10, 2),
    target_fat DECIMAL(10, 2),
    target_carbs DECIMAL(10, 2),
    target_sodium DECIMAL(10, 2),
    target_sugar DECIMAL(10, 2),
    visibility_config JSONB NOT NULL DEFAULT '{}',
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_nutrition_health_profile_member UNIQUE (member_profile_id)
);

CREATE INDEX idx_nutrition_health_profile_family ON nutrition_health_profile (family_id);
CREATE INDEX idx_nutrition_health_profile_member ON nutrition_health_profile (member_profile_id);

CREATE TABLE nutrition_scoped_role_binding (
    id BIGSERIAL PRIMARY KEY,
    subject_type VARCHAR(32) NOT NULL,
    subject_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_nutrition_role_binding_scope UNIQUE (subject_type, subject_id, role_code, scope_type, scope_id)
);

CREATE INDEX idx_nutrition_role_binding_subject ON nutrition_scoped_role_binding (subject_type, subject_id, status);
CREATE INDEX idx_nutrition_role_binding_scope ON nutrition_scoped_role_binding (scope_type, scope_id, status);

CREATE TABLE nutrition_data_grant (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    member_profile_id BIGINT,
    grantee_type VARCHAR(32) NOT NULL,
    grantee_id BIGINT NOT NULL,
    data_scope VARCHAR(64) NOT NULL,
    permission_level VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_data_grant_family ON nutrition_data_grant (family_id, status);
CREATE INDEX idx_nutrition_data_grant_member ON nutrition_data_grant (member_profile_id);
CREATE INDEX idx_nutrition_data_grant_grantee ON nutrition_data_grant (grantee_type, grantee_id, status);
CREATE INDEX idx_nutrition_data_grant_expires_at ON nutrition_data_grant (expires_at);

CREATE TABLE nutrition_standard_food (
    id BIGSERIAL PRIMARY KEY,
    name_cn VARCHAR(128) NOT NULL,
    name_en VARCHAR(128),
    aliases JSONB NOT NULL DEFAULT '[]',
    category VARCHAR(64) NOT NULL,
    external_source VARCHAR(64),
    external_food_id VARCHAR(128),
    calories_per_100g DECIMAL(12, 3),
    protein_per_100g DECIMAL(12, 3),
    fat_per_100g DECIMAL(12, 3),
    carbs_per_100g DECIMAL(12, 3),
    sugar_per_100g DECIMAL(12, 3),
    sodium_per_100g DECIMAL(12, 3),
    fiber_per_100g DECIMAL(12, 3),
    cholesterol_per_100g DECIMAL(12, 3),
    purine_level VARCHAR(32),
    gi_value DECIMAL(8, 3),
    allergen_tags JSONB NOT NULL DEFAULT '[]',
    suitable_tags JSONB NOT NULL DEFAULT '[]',
    data_quality VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_standard_food_category ON nutrition_standard_food (category, status);
CREATE INDEX idx_nutrition_standard_food_external ON nutrition_standard_food (external_source, external_food_id);

CREATE TABLE nutrition_health_tag (
    id BIGSERIAL PRIMARY KEY,
    tag_type VARCHAR(64) NOT NULL,
    tag_code VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_nutrition_health_tag_code UNIQUE (tag_type, tag_code)
);

CREATE INDEX idx_nutrition_health_tag_status ON nutrition_health_tag (tag_type, status);

CREATE TABLE nutrition_recipe (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT,
    source_type VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(64),
    description TEXT NOT NULL DEFAULT '',
    serving_count INTEGER NOT NULL DEFAULT 1,
    cooking_minutes INTEGER,
    difficulty_level VARCHAR(32),
    suitable_tags JSONB NOT NULL DEFAULT '[]',
    allergen_tags JSONB NOT NULL DEFAULT '[]',
    nutrition_snapshot JSONB NOT NULL DEFAULT '{}',
    estimated_cost DECIMAL(14, 2),
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_recipe_family ON nutrition_recipe (family_id, status);
CREATE INDEX idx_nutrition_recipe_source_status ON nutrition_recipe (source_type, status);
CREATE INDEX idx_nutrition_recipe_category ON nutrition_recipe (category);

CREATE TABLE nutrition_recipe_ingredient (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT,
    recipe_id BIGINT NOT NULL,
    standard_food_id BIGINT,
    raw_food_name VARCHAR(128) NOT NULL,
    amount DECIMAL(14, 3) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    mapping_status VARCHAR(32) NOT NULL,
    optional BOOLEAN NOT NULL DEFAULT FALSE,
    nutrition_snapshot JSONB NOT NULL DEFAULT '{}',
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_recipe_ingredient_family ON nutrition_recipe_ingredient (family_id);
CREATE INDEX idx_nutrition_recipe_ingredient_recipe ON nutrition_recipe_ingredient (recipe_id);
CREATE INDEX idx_nutrition_recipe_ingredient_food ON nutrition_recipe_ingredient (standard_food_id);

CREATE TABLE nutrition_recipe_step (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT,
    recipe_id BIGINT NOT NULL,
    step_no INTEGER NOT NULL,
    title VARCHAR(128),
    instruction TEXT NOT NULL DEFAULT '',
    media_metadata JSONB NOT NULL DEFAULT '{}',
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_recipe_step_family ON nutrition_recipe_step (family_id);
CREATE INDEX idx_nutrition_recipe_step_recipe ON nutrition_recipe_step (recipe_id, step_no);

CREATE TABLE nutrition_import_job (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT,
    import_type VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_key VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    total_rows INTEGER NOT NULL DEFAULT 0,
    success_rows INTEGER NOT NULL DEFAULT 0,
    failed_rows INTEGER NOT NULL DEFAULT 0,
    warning_rows INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    error_summary TEXT,
    preview_snapshot JSONB NOT NULL DEFAULT '{}',
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_import_job_family ON nutrition_import_job (family_id, status);
CREATE INDEX idx_nutrition_import_job_status ON nutrition_import_job (status, created_at);
CREATE INDEX idx_nutrition_import_job_type_status ON nutrition_import_job (import_type, status);

CREATE TABLE nutrition_import_error (
    id BIGSERIAL PRIMARY KEY,
    import_job_id BIGINT NOT NULL,
    row_no INTEGER NOT NULL,
    column_name VARCHAR(128),
    error_code VARCHAR(64) NOT NULL,
    error_message VARCHAR(1024) NOT NULL,
    raw_row_snapshot JSONB NOT NULL DEFAULT '{}',
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_import_error_job ON nutrition_import_error (import_job_id, row_no);

CREATE TABLE nutrition_ai_recommendation_job (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_by BIGINT,
    planned_date DATE NOT NULL,
    target_meal_types JSONB NOT NULL DEFAULT '[]',
    input_snapshot JSONB NOT NULL DEFAULT '{}',
    output_snapshot JSONB NOT NULL DEFAULT '{}',
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_ai_recommendation_job_family ON nutrition_ai_recommendation_job (family_id, planned_date);
CREATE INDEX idx_nutrition_ai_recommendation_job_status ON nutrition_ai_recommendation_job (status, created_at);
CREATE INDEX idx_nutrition_ai_recommendation_job_trigger ON nutrition_ai_recommendation_job (trigger_type, status);

CREATE TABLE nutrition_ai_recommendation (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    ai_job_id BIGINT NOT NULL,
    recommendation_date DATE NOT NULL,
    title VARCHAR(128) NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    meal_types JSONB NOT NULL DEFAULT '[]',
    input_snapshot JSONB NOT NULL DEFAULT '{}',
    output_snapshot JSONB NOT NULL DEFAULT '{}',
    risk_summary JSONB NOT NULL DEFAULT '{}',
    cost_estimate DECIMAL(14, 2),
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_ai_recommendation_family_date ON nutrition_ai_recommendation (family_id, recommendation_date);
CREATE INDEX idx_nutrition_ai_recommendation_job ON nutrition_ai_recommendation (ai_job_id);

CREATE TABLE nutrition_risk_check_result (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    member_profile_id BIGINT,
    source_type VARCHAR(64) NOT NULL,
    source_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    risk_message VARCHAR(1024) NOT NULL,
    risk_snapshot JSONB NOT NULL DEFAULT '{}',
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_risk_check_family ON nutrition_risk_check_result (family_id, risk_level);
CREATE INDEX idx_nutrition_risk_check_member ON nutrition_risk_check_result (member_profile_id, risk_level);
CREATE INDEX idx_nutrition_risk_check_source ON nutrition_risk_check_result (source_type, source_id);

CREATE TABLE nutrition_meal_plan (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    ai_recommendation_id BIGINT,
    plan_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    confirmation_cutoff_at TIMESTAMP WITH TIME ZONE,
    confirmed_member_count INTEGER NOT NULL DEFAULT 0,
    estimated_cost DECIMAL(14, 2),
    nutrition_snapshot JSONB NOT NULL DEFAULT '{}',
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_meal_plan_family ON nutrition_meal_plan (family_id, status);
CREATE INDEX idx_nutrition_meal_plan_family_date ON nutrition_meal_plan (family_id, plan_date);
CREATE INDEX idx_nutrition_meal_plan_date_status ON nutrition_meal_plan (plan_date, status);

CREATE TABLE nutrition_meal_plan_item (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    meal_plan_id BIGINT NOT NULL,
    meal_type VARCHAR(32) NOT NULL,
    recipe_id BIGINT,
    dish_name VARCHAR(128) NOT NULL,
    serving_count DECIMAL(12, 3) NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0,
    nutrition_snapshot JSONB NOT NULL DEFAULT '{}',
    cost_snapshot JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_meal_plan_item_family ON nutrition_meal_plan_item (family_id, meal_type);
CREATE INDEX idx_nutrition_meal_plan_item_plan ON nutrition_meal_plan_item (meal_plan_id, sort_order);
CREATE INDEX idx_nutrition_meal_plan_item_recipe ON nutrition_meal_plan_item (recipe_id);

CREATE TABLE nutrition_meal_confirmation (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    meal_plan_id BIGINT NOT NULL,
    member_profile_id BIGINT NOT NULL,
    confirmed_by_user_id BIGINT,
    proxy_by_user_id BIGINT,
    confirmation_status VARCHAR(32) NOT NULL,
    eat_at_home BOOLEAN NOT NULL DEFAULT TRUE,
    selected_meal_types JSONB NOT NULL DEFAULT '[]',
    risk_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    risk_confirmation_note VARCHAR(512),
    remark VARCHAR(512),
    confirmed_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_nutrition_meal_confirmation_member UNIQUE (meal_plan_id, member_profile_id)
);

CREATE INDEX idx_nutrition_meal_confirmation_family ON nutrition_meal_confirmation (family_id, confirmation_status);
CREATE INDEX idx_nutrition_meal_confirmation_member ON nutrition_meal_confirmation (member_profile_id, confirmation_status);
CREATE INDEX idx_nutrition_meal_confirmation_plan ON nutrition_meal_confirmation (meal_plan_id);

CREATE TABLE nutrition_meal_confirmation_item (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    confirmation_id BIGINT NOT NULL,
    meal_plan_item_id BIGINT NOT NULL,
    meal_type VARCHAR(32) NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT TRUE,
    serving_count DECIMAL(12, 3) NOT NULL DEFAULT 1,
    risk_acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    adjustment_note VARCHAR(512),
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_meal_confirmation_item_family ON nutrition_meal_confirmation_item (family_id);
CREATE INDEX idx_nutrition_meal_confirmation_item_confirmation ON nutrition_meal_confirmation_item (confirmation_id);
CREATE INDEX idx_nutrition_meal_confirmation_item_plan_item ON nutrition_meal_confirmation_item (meal_plan_item_id);

CREATE TABLE nutrition_meal_operation_log (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    meal_plan_id BIGINT NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    before_snapshot JSONB NOT NULL DEFAULT '{}',
    after_snapshot JSONB NOT NULL DEFAULT '{}',
    note VARCHAR(512),
    operated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_meal_operation_log_family ON nutrition_meal_operation_log (family_id, operated_at);
CREATE INDEX idx_nutrition_meal_operation_log_plan ON nutrition_meal_operation_log (meal_plan_id, operated_at);
CREATE INDEX idx_nutrition_meal_operation_log_operator ON nutrition_meal_operation_log (operator_user_id);

CREATE TABLE nutrition_shopping_list (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    meal_plan_id BIGINT,
    list_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    generated_snapshot JSONB NOT NULL DEFAULT '{}',
    estimated_total_price DECIMAL(14, 2),
    actual_total_price DECIMAL(14, 2),
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_shopping_list_family ON nutrition_shopping_list (family_id, status);
CREATE INDEX idx_nutrition_shopping_list_family_date ON nutrition_shopping_list (family_id, list_date);
CREATE INDEX idx_nutrition_shopping_list_meal_plan ON nutrition_shopping_list (meal_plan_id);

CREATE TABLE nutrition_shopping_list_item (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    shopping_list_id BIGINT NOT NULL,
    standard_food_id BIGINT,
    raw_food_name VARCHAR(128) NOT NULL,
    category VARCHAR(64),
    planned_amount DECIMAL(14, 3) NOT NULL,
    planned_unit VARCHAR(32) NOT NULL,
    purchased_amount DECIMAL(14, 3),
    purchased_unit VARCHAR(32),
    channel VARCHAR(128),
    brand VARCHAR(128),
    spec_amount DECIMAL(14, 3),
    spec_unit VARCHAR(32),
    total_price DECIMAL(14, 2),
    normalized_unit_price DECIMAL(14, 4),
    item_status VARCHAR(32) NOT NULL,
    note VARCHAR(512),
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_shopping_list_item_family ON nutrition_shopping_list_item (family_id, item_status);
CREATE INDEX idx_nutrition_shopping_list_item_list ON nutrition_shopping_list_item (shopping_list_id);
CREATE INDEX idx_nutrition_shopping_list_item_food ON nutrition_shopping_list_item (standard_food_id);

CREATE TABLE nutrition_food_price_record (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    shopping_list_item_id BIGINT,
    standard_food_id BIGINT,
    raw_food_name VARCHAR(128) NOT NULL,
    price_date DATE NOT NULL,
    channel VARCHAR(128),
    brand VARCHAR(128),
    spec_amount DECIMAL(14, 3),
    spec_unit VARCHAR(32),
    purchase_quantity DECIMAL(14, 3),
    total_price DECIMAL(14, 2) NOT NULL,
    normalized_unit_price DECIMAL(14, 4),
    source_type VARCHAR(32) NOT NULL,
    note VARCHAR(512),
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_food_price_record_family ON nutrition_food_price_record (family_id);
CREATE INDEX idx_nutrition_food_price_record_family_date ON nutrition_food_price_record (family_id, price_date);
CREATE INDEX idx_nutrition_food_price_record_food_date ON nutrition_food_price_record (standard_food_id, price_date);

CREATE TABLE nutrition_budget_rule (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    rule_name VARCHAR(128) NOT NULL,
    period_type VARCHAR(32) NOT NULL,
    amount_limit DECIMAL(14, 2) NOT NULL,
    currency VARCHAR(16) NOT NULL DEFAULT 'CNY',
    warning_threshold DECIMAL(8, 4),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_budget_rule_family ON nutrition_budget_rule (family_id, status);

CREATE TABLE nutrition_budget_snapshot (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    snapshot_date DATE NOT NULL,
    period_type VARCHAR(32) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    planned_cost DECIMAL(14, 2),
    actual_cost DECIMAL(14, 2),
    per_person_cost DECIMAL(14, 2),
    budget_limit DECIMAL(14, 2),
    usage_rate DECIMAL(8, 4),
    summary_snapshot JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_budget_snapshot_family_date ON nutrition_budget_snapshot (family_id, snapshot_date);
CREATE INDEX idx_nutrition_budget_snapshot_period ON nutrition_budget_snapshot (family_id, period_type, period_start, period_end);

CREATE TABLE nutrition_record (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    member_profile_id BIGINT NOT NULL,
    meal_plan_id BIGINT,
    meal_confirmation_id BIGINT,
    record_date DATE NOT NULL,
    meal_type VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    calories DECIMAL(12, 3),
    protein DECIMAL(12, 3),
    fat DECIMAL(12, 3),
    carbs DECIMAL(12, 3),
    sugar DECIMAL(12, 3),
    sodium DECIMAL(12, 3),
    fiber DECIMAL(12, 3),
    cholesterol DECIMAL(12, 3),
    risk_tags JSONB NOT NULL DEFAULT '[]',
    calculation_snapshot JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_record_family_date ON nutrition_record (family_id, record_date);
CREATE INDEX idx_nutrition_record_member_date ON nutrition_record (member_profile_id, record_date);
CREATE INDEX idx_nutrition_record_meal_plan ON nutrition_record (meal_plan_id);

CREATE TABLE nutrition_record_adjustment (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    nutrition_record_id BIGINT NOT NULL,
    member_profile_id BIGINT NOT NULL,
    adjusted_by_user_id BIGINT NOT NULL,
    adjustment_type VARCHAR(64) NOT NULL,
    before_snapshot JSONB NOT NULL DEFAULT '{}',
    after_snapshot JSONB NOT NULL DEFAULT '{}',
    reason VARCHAR(512),
    adjusted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_record_adjustment_record ON nutrition_record_adjustment (nutrition_record_id, adjusted_at);
CREATE INDEX idx_nutrition_record_adjustment_member ON nutrition_record_adjustment (family_id, member_profile_id);

CREATE TABLE nutrition_extra_food_record (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    member_profile_id BIGINT NOT NULL,
    record_date DATE NOT NULL,
    meal_type VARCHAR(32) NOT NULL,
    food_name VARCHAR(128) NOT NULL,
    standard_food_id BIGINT,
    amount DECIMAL(14, 3) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    nutrition_snapshot JSONB NOT NULL DEFAULT '{}',
    note VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_extra_food_record_family_member_date ON nutrition_extra_food_record (family_id, member_profile_id, record_date);
CREATE INDEX idx_nutrition_extra_food_record_food ON nutrition_extra_food_record (standard_food_id);

CREATE TABLE nutrition_report_snapshot (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL,
    member_profile_id BIGINT,
    report_type VARCHAR(64) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    report_snapshot JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(32) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_nutrition_report_snapshot_family_period ON nutrition_report_snapshot (family_id, report_type, period_start, period_end);
CREATE INDEX idx_nutrition_report_snapshot_member_period ON nutrition_report_snapshot (member_profile_id, period_start, period_end);
