-- Complete reviewed Clocktower base-script rule data and convert Clocktower coded enums to integer storage.
-- Generated from docs/clocktower/rule-data CSV source files. Do not edit V18.

ALTER TABLE clocktower_role
    ALTER COLUMN role_type TYPE INTEGER USING CASE
                                                  WHEN role_type = 'TOWNSFOLK' THEN 1
                                                  WHEN role_type = 'OUTSIDER' THEN 2
                                                  WHEN role_type = 'MINION' THEN 3
                                                  WHEN role_type = 'DEMON' THEN 4
                                                  WHEN role_type = 'TRAVELER' THEN 5
                                                  WHEN role_type = 'FABLED' THEN 6
                                                  ELSE CAST(role_type AS INTEGER)
        END;

ALTER TABLE clocktower_role
    ALTER COLUMN alignment TYPE INTEGER USING CASE
                                                  WHEN alignment = 'GOOD' THEN 1
                                                  WHEN alignment = 'EVIL' THEN 2
                                                  WHEN alignment = 'NEUTRAL' THEN 3
                                                  ELSE CAST(alignment AS INTEGER)
        END;

ALTER TABLE clocktower_board_role
    ALTER COLUMN role_type TYPE INTEGER USING CASE
                                                  WHEN role_type = 'TOWNSFOLK' THEN 1
                                                  WHEN role_type = 'OUTSIDER' THEN 2
                                                  WHEN role_type = 'MINION' THEN 3
                                                  WHEN role_type = 'DEMON' THEN 4
                                                  WHEN role_type = 'TRAVELER' THEN 5
                                                  WHEN role_type = 'FABLED' THEN 6
                                                  ELSE CAST(role_type AS INTEGER)
        END;

ALTER TABLE clocktower_seat
    ALTER COLUMN role_type TYPE INTEGER USING CASE
                                                  WHEN role_type IS NULL THEN NULL
                                                  WHEN role_type = 'TOWNSFOLK' THEN 1
                                                  WHEN role_type = 'OUTSIDER' THEN 2
                                                  WHEN role_type = 'MINION' THEN 3
                                                  WHEN role_type = 'DEMON' THEN 4
                                                  WHEN role_type = 'TRAVELER' THEN 5
                                                  WHEN role_type = 'FABLED' THEN 6
                                                  ELSE CAST(role_type AS INTEGER)
        END;

ALTER TABLE clocktower_seat
    ALTER COLUMN alignment TYPE INTEGER USING CASE
                                                  WHEN alignment IS NULL THEN NULL
                                                  WHEN alignment = 'GOOD' THEN 1
                                                  WHEN alignment = 'EVIL' THEN 2
                                                  WHEN alignment = 'NEUTRAL' THEN 3
                                                  ELSE CAST(alignment AS INTEGER)
        END;

ALTER TABLE clocktower_grimoire_entry
    ALTER COLUMN role_type TYPE INTEGER USING CASE
                                                  WHEN role_type = 'TOWNSFOLK' THEN 1
                                                  WHEN role_type = 'OUTSIDER' THEN 2
                                                  WHEN role_type = 'MINION' THEN 3
                                                  WHEN role_type = 'DEMON' THEN 4
                                                  WHEN role_type = 'TRAVELER' THEN 5
                                                  WHEN role_type = 'FABLED' THEN 6
                                                  ELSE CAST(role_type AS INTEGER)
        END;

ALTER TABLE clocktower_grimoire_entry
    ALTER COLUMN alignment TYPE INTEGER USING CASE
                                                  WHEN alignment = 'GOOD' THEN 1
                                                  WHEN alignment = 'EVIL' THEN 2
                                                  WHEN alignment = 'NEUTRAL' THEN 3
                                                  ELSE CAST(alignment AS INTEGER)
        END;

ALTER TABLE clocktower_night_order
    ALTER COLUMN night_type TYPE INTEGER USING CASE
                                                   WHEN night_type = 'FIRST_NIGHT' THEN 1
                                                   WHEN night_type = 'OTHER_NIGHT' THEN 2
                                                   ELSE CAST(night_type AS INTEGER)
        END;

UPDATE clocktower_script
SET role_count = 22,
    updated_at = CURRENT_TIMESTAMP,
    version    = version + 1
WHERE script_code = 'TROUBLE_BREWING';
UPDATE clocktower_script
SET role_count = 25,
    updated_at = CURRENT_TIMESTAMP,
    version    = version + 1
WHERE script_code = 'BAD_MOON_RISING';
UPDATE clocktower_script
SET role_count = 25,
    updated_at = CURRENT_TIMESTAMP,
    version    = version + 1
WHERE script_code = 'SECTS_AND_VIOLETS';

DELETE
FROM clocktower_role
WHERE script_code IN ('TROUBLE_BREWING', 'BAD_MOON_RISING', 'SECTS_AND_VIOLETS');

INSERT INTO clocktower_role (script_code, role_code, name, role_type, alignment, ability_text, first_night, other_night,
                             setup_modifier, complexity, first_night_order, other_night_order,
                             first_night_reminder, other_night_reminder, source_url, enabled, sort_order)
VALUES ('TROUBLE_BREWING', 'WASHERWOMAN', '洗衣妇', 1, 1, '在你的首个夜晚，你会得知两名玩家中有一名是某个镇民角色。',
        TRUE, FALSE, FALSE, 1, 2, NULL, '展示那个镇民角色标记。指向被你标记“镇民”和“错误”的两名玩家。', NULL,
        'https://wiki.bloodontheclocktower.com/Washerwoman', TRUE, 10),
       ('TROUBLE_BREWING', 'LIBRARIAN', '图书管理员', 1, 1,
        '在你的首个夜晚，你会得知两名玩家中有一名是某个外来者角色。（或者得知没有外来者在场。）', TRUE, FALSE, FALSE, 1, 3,
        NULL, '展示那个外来者角色标记。指向被你标记“外来者”和“错误”的两名玩家。', NULL,
        'https://wiki.bloodontheclocktower.com/Librarian', TRUE, 20),
       ('TROUBLE_BREWING', 'INVESTIGATOR', '调查员', 1, 1, '在你的首个夜晚，你会得知两名玩家中有一名是某个爪牙角色。',
        TRUE, FALSE, FALSE, 1, 4, NULL, '展示那个爪牙角色标记。指向被你标记“爪牙”和“错误”的两名玩家。', NULL,
        'https://wiki.bloodontheclocktower.com/Investigator', TRUE, 30),
       ('TROUBLE_BREWING', 'CHEF', '厨师', 1, 1, '在你的首个夜晚，你会得知场上相邻的邪恶玩家有多少对。', TRUE, FALSE,
        FALSE, 1, 5, NULL, '给他展示数字手势来告诉他场上邻座邪恶玩家有多少对。', NULL,
        'https://wiki.bloodontheclocktower.com/Chef', TRUE, 40),
       ('TROUBLE_BREWING', 'EMPATH', '共情者', 1, 1, '每个夜晚，你会得知与你邻近的两名存活玩家中有几名是邪恶的。', TRUE,
        TRUE, FALSE, 1, 6, 6, '给他展示数字手势来告诉他与他邻近的存活玩家有几人是邪恶的。',
        '给他展示数字手势来告诉他与他邻近的存活玩家有几人是邪恶的。', 'https://wiki.bloodontheclocktower.com/Empath',
        TRUE, 50),
       ('TROUBLE_BREWING', 'FORTUNETELLER', '占卜师', 1, 1,
        '每个夜晚，选择两名玩家：你会得知其中是否有恶魔。有一名善良玩家会被你的能力当作恶魔。', TRUE, TRUE, FALSE, 1, 7, 7,
        '让占卜师选择两名玩家。如果其中有恶魔或“干扰项”，点头示意，否则摇头。',
        '让占卜师选择两名玩家。如果其中有恶魔或“干扰项”，点头示意，否则摇头。',
        'https://wiki.bloodontheclocktower.com/Fortune_Teller', TRUE, 60),
       ('TROUBLE_BREWING', 'UNDERTAKER', '送葬者', 1, 1, '每个夜晚*，你会得知今天白天哪名角色死于处决。', FALSE, TRUE,
        FALSE, 1, NULL, 8, NULL, '如果有玩家今天白天死于处决，唤醒送葬者并对他展示那名玩家的角色标记。',
        'https://wiki.bloodontheclocktower.com/Undertaker', TRUE, 70),
       ('TROUBLE_BREWING', 'MONK', '僧侣', 1, 1, '每个夜晚*，选择一名玩家（不能选择自己）：他今晚免受恶魔伤害。', FALSE,
        TRUE, FALSE, 1, NULL, 2, NULL, '让僧侣选择除自己外的一名玩家。标记那名玩家被保护。',
        'https://wiki.bloodontheclocktower.com/Monk', TRUE, 80),
       ('TROUBLE_BREWING', 'RAVENKEEPER', '守鸦人', 1, 1, '如果你在夜晚死亡，你会被唤醒并选择一名玩家：你会得知他的角色。',
        FALSE, TRUE, FALSE, 1, NULL, 5, NULL, '如果守鸦人今晚死亡，唤醒他并让他选择一名玩家。对他展示那名玩家的角色标记。',
        'https://wiki.bloodontheclocktower.com/Ravenkeeper', TRUE, 90),
       ('TROUBLE_BREWING', 'VIRGIN', '贞洁者', 1, 1, '你第一次被提名时，如果提名者是镇民，他会立刻被处决。', FALSE, FALSE,
        FALSE, 1, NULL, NULL, NULL, NULL, 'https://wiki.bloodontheclocktower.com/Virgin', TRUE, 100),
       ('TROUBLE_BREWING', 'SLAYER', '猎手', 1, 1, '每局游戏限一次，在白天公开选择一名玩家：如果他是恶魔，他死亡。', FALSE,
        FALSE, FALSE, 1, NULL, NULL, NULL, NULL, 'https://wiki.bloodontheclocktower.com/Slayer', TRUE, 110),
       ('TROUBLE_BREWING', 'SOLDIER', '士兵', 1, 1, '你免受恶魔伤害。', FALSE, FALSE, FALSE, 1, NULL, NULL, NULL, NULL,
        'https://wiki.bloodontheclocktower.com/Soldier', TRUE, 120),
       ('TROUBLE_BREWING', 'MAYOR', '镇长', 1, 1,
        '如果只有三名玩家存活且无人被处决，你的阵营获胜。如果你在夜晚死亡，可能会改为另一名玩家死亡。', FALSE, FALSE, FALSE,
        1, NULL, NULL, NULL, NULL, 'https://wiki.bloodontheclocktower.com/Mayor', TRUE, 130),
       ('TROUBLE_BREWING', 'BUTLER', '管家', 2, 1, '每个夜晚，选择一名玩家（不能选择自己）：明天，你只能在他投票时投票。',
        TRUE, TRUE, FALSE, 1, 8, 9, '让管家选择一名玩家。标记那名玩家为他的主人。',
        '让管家选择一名玩家。标记那名玩家为他的主人。', 'https://wiki.bloodontheclocktower.com/Butler', TRUE, 140),
       ('TROUBLE_BREWING', 'DRUNK', '酒鬼', 2, 1, '你不知道自己是酒鬼。你以为自己是某个镇民角色，但其实不是。', FALSE,
        FALSE, FALSE, 1, NULL, NULL, NULL, NULL, 'https://wiki.bloodontheclocktower.com/Drunk', TRUE, 150),
       ('TROUBLE_BREWING', 'RECLUSE', '陌客', 2, 1, '即使死亡，你也可能被当作邪恶阵营、爪牙或恶魔。', FALSE, FALSE, FALSE,
        1, NULL, NULL, NULL, NULL, 'https://wiki.bloodontheclocktower.com/Recluse', TRUE, 160),
       ('TROUBLE_BREWING', 'SAINT', '圣徒', 2, 1, '如果你死于处决，你的阵营落败。', FALSE, FALSE, FALSE, 1, NULL, NULL,
        NULL, NULL, 'https://wiki.bloodontheclocktower.com/Saint', TRUE, 170),
       ('TROUBLE_BREWING', 'POISONER', '投毒者', 3, 2, '每个夜晚，选择一名玩家：他今晚和明天白天中毒。', TRUE, TRUE, FALSE,
        1, 1, 1, '让投毒者选择一名玩家。标记那名玩家中毒。', '让投毒者选择一名玩家。标记那名玩家中毒。',
        'https://wiki.bloodontheclocktower.com/Poisoner', TRUE, 180),
       ('TROUBLE_BREWING', 'SPY', '间谍', 3, 2, '每个夜晚，你会看到魔典。即使死亡，你也可能被当作善良阵营、镇民或外来者。',
        TRUE, TRUE, FALSE, 1, 9, 10, '将魔典展示给间谍，他想看多久就看多久。', '将魔典展示给间谍，他想看多久就看多久。',
        'https://wiki.bloodontheclocktower.com/Spy', TRUE, 190),
       ('TROUBLE_BREWING', 'SCARLETWOMAN', '红唇女郎', 3, 2,
        '如果有五名或更多玩家存活且恶魔死亡，你变成恶魔。（旅行者不计入人数。）', FALSE, TRUE, FALSE, 1, NULL, 3, NULL,
        '如果红唇女郎今天变成了小恶魔，对她展示“你是”信息标记，和小恶魔角色标记。',
        'https://wiki.bloodontheclocktower.com/Scarlet_Woman', TRUE, 200),
       ('TROUBLE_BREWING', 'BARON', '男爵', 3, 2, '有额外的外来者在场。[+2 外来者]', FALSE, FALSE, FALSE, 1, NULL, NULL,
        NULL, NULL, 'https://wiki.bloodontheclocktower.com/Baron', TRUE, 210),
       ('TROUBLE_BREWING', 'IMP', '小恶魔', 4, 2,
        '每个夜晚*，选择一名玩家：他死亡。如果你以这种方式杀死自己，一名爪牙变成小恶魔。', FALSE, TRUE, FALSE, 1, NULL, 4,
        NULL,
        '让小恶魔选择一名玩家。标记那名玩家死亡。如果小恶魔选择了自己：用一个备用的小恶魔标记替换一个存活的爪牙角色标记。让原来的小恶魔重新入睡。唤醒新的小恶魔。对他展示“你是”信息标记，和小恶魔角色标记。',
        'https://wiki.bloodontheclocktower.com/Imp', TRUE, 220),
       ('BAD_MOON_RISING', 'GRANDMOTHER', '祖母', 1, 1,
        '在你的首个夜晚，你会得知一名善良玩家及其角色。如果恶魔杀死他，你也会死亡。', TRUE, TRUE, FALSE, 1, 7, 18,
        '指向她的孙子玩家，并展示该玩家的角色标记。', '如果孙子被恶魔杀死，祖母也会一同死亡。标记祖母死亡。',
        'https://wiki.bloodontheclocktower.com/Grandmother', TRUE, 10),
       ('BAD_MOON_RISING', 'SAILOR', '水手', 1, 1, '每个夜晚，选择一名存活玩家：你或他醉酒直到黄昏。你不会死亡。', TRUE,
        TRUE, FALSE, 1, 2, 1, '让水手选择一名存活玩家。标记那名玩家或水手醉酒。',
        '让水手选择一名存活玩家。标记那名玩家或水手醉酒。', 'https://wiki.bloodontheclocktower.com/Sailor', TRUE, 20),
       ('BAD_MOON_RISING', 'CHAMBERMAID', '侍女', 1, 1,
        '每个夜晚，选择除你以外的两名存活玩家：你会得知他们中有几人今晚因自身能力被唤醒。', TRUE, TRUE, FALSE, 1, 8, 19,
        '让侍女选择除自己外的两名存活玩家。给她展示数字手势来告诉她这些玩家中有几人因自身能力被唤醒。',
        '让侍女选择除自己外的两名存活玩家。给她展示数字手势来告诉她这些玩家中有几人因自身能力被唤醒。',
        'https://wiki.bloodontheclocktower.com/Chambermaid', TRUE, 30),
       ('BAD_MOON_RISING', 'EXORCIST', '驱魔人', 1, 1,
        '每个夜晚*，选择一名与上一夜不同的玩家：如果选中恶魔，恶魔会得知你是谁，然后今晚不会醒来。', FALSE, TRUE, FALSE, 1,
        NULL, 7, NULL,
        '让驱魔人选择一名玩家，不能是上一夜他选择过的玩家。让驱魔人重新入睡。如果驱魔人选中了恶魔：唤醒恶魔。展示“该角色的能力对你生效”信息标记和驱魔人角色标记。指向驱魔人玩家。',
        'https://wiki.bloodontheclocktower.com/Exorcist', TRUE, 40),
       ('BAD_MOON_RISING', 'INNKEEPER', '旅店老板', 1, 1,
        '每个夜晚*，选择两名玩家：他们今晚不会死亡，但其中一人醉酒直到黄昏。', FALSE, TRUE, FALSE, 1, NULL, 3, NULL,
        '让旅店老板选择两名玩家。标记这两名玩家不会死亡，并标记其中一人醉酒。',
        'https://wiki.bloodontheclocktower.com/Innkeeper', TRUE, 50),
       ('BAD_MOON_RISING', 'GAMBLER', '赌徒', 1, 1, '每个夜晚*，选择一名玩家并猜测他的角色：如果你猜错，你死亡。', FALSE,
        TRUE, FALSE, 1, NULL, 4, NULL, '让赌徒选择一名玩家和一个角色。如果赌徒猜错了，标记赌徒死亡。',
        'https://wiki.bloodontheclocktower.com/Gambler', TRUE, 60),
       ('BAD_MOON_RISING', 'GOSSIP', '造谣者', 1, 1,
        '每个白天，你可以公开发表一条陈述。今晚，如果该陈述为真，一名玩家死亡。', FALSE, TRUE, FALSE, 1, NULL, 14, NULL,
        '如果白天的声明为真，会有一名玩家死亡，并由说书人来选择一名玩家，标记该玩家死亡。',
        'https://wiki.bloodontheclocktower.com/Gossip', TRUE, 70),
       ('BAD_MOON_RISING', 'COURTIER', '侍臣', 1, 1, '每局游戏限一次，在夜晚选择一个角色：该角色醉酒三个夜晚和三个白天。',
        TRUE, TRUE, FALSE, 1, 3, 2,
        '侍臣可以选择一个角色。如果他这么做了，标记侍臣失去能力，标记被选择的角色所对应的玩家醉酒。之后的夜晚无需再唤醒侍臣。',
        '侍臣可以选择一个角色。如果他这么做了，标记侍臣失去能力，标记被选择的角色所对应的玩家醉酒。之后的夜晚无需再唤醒侍臣。',
        'https://wiki.bloodontheclocktower.com/Courtier', TRUE, 80),
       ('BAD_MOON_RISING', 'PROFESSOR', '教授', 1, 1, '每局游戏限一次，在夜晚*选择一名死亡玩家：如果他是镇民，他复活。',
        FALSE, TRUE, FALSE, 1, NULL, 15, NULL,
        '教授可以选择一名死亡玩家。如果他这么做了，标记教授失去能力，然后如果那名玩家是镇民，标记那名玩家被复活。之后的夜晚无需再唤醒教授。',
        'https://wiki.bloodontheclocktower.com/Professor', TRUE, 90),
       ('BAD_MOON_RISING', 'MINSTREL', '吟游诗人', 1, 1,
        '当一名爪牙死于处决时，所有其他玩家（旅行者除外）醉酒直到明天黄昏。', FALSE, FALSE, FALSE, 1, NULL, NULL, NULL,
        NULL, 'https://wiki.bloodontheclocktower.com/Minstrel', TRUE, 100),
       ('BAD_MOON_RISING', 'TEALADY', '茶艺师', 1, 1, '如果与你邻近的两名存活玩家都是善良的，他们不会死亡。', FALSE,
        FALSE, FALSE, 1, NULL, NULL, NULL, NULL, 'https://wiki.bloodontheclocktower.com/Tea_Lady', TRUE, 110),
       ('BAD_MOON_RISING', 'PACIFIST', '和平主义者', 1, 1, '被处决的善良玩家可能不会死亡。', FALSE, FALSE, FALSE, 1,
        NULL, NULL, NULL, NULL, 'https://wiki.bloodontheclocktower.com/Pacifist', TRUE, 120),
       ('BAD_MOON_RISING', 'FOOL', '弄臣', 1, 1, '你第一次将要死亡时，不会死亡。', FALSE, FALSE, FALSE, 1, NULL, NULL,
        NULL, NULL, 'https://wiki.bloodontheclocktower.com/Fool', TRUE, 130),
       ('BAD_MOON_RISING', 'GOON', '莽夫', 2, 1, '每个夜晚，第一名用能力选择你的玩家醉酒直到黄昏。你变成他的阵营。', FALSE,
        FALSE, FALSE, 1, NULL, NULL, NULL, NULL, 'https://wiki.bloodontheclocktower.com/Goon', TRUE, 140),
       ('BAD_MOON_RISING', 'LUNATIC', '疯子', 2, 1, '你以为自己是恶魔，但其实不是。恶魔知道你是谁以及你在夜晚选择了谁。',
        TRUE, TRUE, FALSE, 1, 1, 6,
        '如果有七名或更多玩家，唤醒疯子：展示“他们是你的爪牙”信息标记。指向任意对应数量的玩家。展示“这些角色不在场”信息标记。展示三个善良角色。让疯子重新入睡。唤醒恶魔。展示“你是”信息标记和恶魔角色标记。展示“这名玩家是”信息标记和疯子角色标记，然后指向疯子玩家。',
        '做任何需要做的事情来模拟一位恶魔的行动。让疯子重新入睡。唤醒恶魔。对恶魔展示疯子角色标记，并指向疯子玩家，随后是疯子的攻击目标。',
        'https://wiki.bloodontheclocktower.com/Lunatic', TRUE, 150),
       ('BAD_MOON_RISING', 'TINKER', '修补匠', 2, 1, '你随时可能死亡。', FALSE, TRUE, FALSE, 1, NULL, 16, NULL,
        '修补匠可能会死亡。如果说书人选择让修补匠死亡，放置死亡标记。', 'https://wiki.bloodontheclocktower.com/Tinker',
        TRUE, 160),
       ('BAD_MOON_RISING', 'MOONCHILD', '月之子', 2, 1,
        '当你得知自己死亡时，公开选择一名存活玩家。今晚，如果他是善良玩家，他死亡。', FALSE, TRUE, FALSE, 1, NULL, 17, NULL,
        '如果月之子在白天触发了死亡能力并选择了一名善良玩家，该玩家死亡。标记那名玩家死亡。',
        'https://wiki.bloodontheclocktower.com/Moonchild', TRUE, 170),
       ('BAD_MOON_RISING', 'GODFATHER', '教父', 3, 2,
        '在你的首个夜晚，你会得知哪些外来者在场。如果今天有一名外来者死亡，今晚选择一名玩家：他死亡。[-1 或 +1 外来者]',
        TRUE, TRUE, FALSE, 1, 4, 13, '对他展示所有在场的外来者标记。',
        '如果有外来者在今天白天死亡，让教父选择一名玩家。标记那名玩家死亡。',
        'https://wiki.bloodontheclocktower.com/Godfather', TRUE, 180),
       ('BAD_MOON_RISING', 'DEVILSADVOCATE', '魔鬼代言人', 3, 2,
        '每个夜晚，选择一名与上一夜不同的存活玩家：如果他明天被处决，他不会死亡。', TRUE, TRUE, FALSE, 1, 5, 5,
        '让魔鬼代言人选择一名存活玩家。标记那名玩家处决不死。',
        '让魔鬼代言人选择一名存活玩家，不能是上一夜他选择过的玩家。标记那名玩家处决不死。',
        'https://wiki.bloodontheclocktower.com/Devil%27s_Advocate', TRUE, 190),
       ('BAD_MOON_RISING', 'ASSASSIN', '刺客', 3, 2,
        '每局游戏限一次，在夜晚*选择一名玩家：他死亡，即使因为某些原因他本来不会死亡。', FALSE, TRUE, FALSE, 1, NULL, 12,
        NULL, '刺客可以选择一名玩家。如果他这么做了，标记那名玩家死亡，且刺客失去能力，之后的夜晚无需再唤醒刺客。',
        'https://wiki.bloodontheclocktower.com/Assassin', TRUE, 200),
       ('BAD_MOON_RISING', 'MASTERMIND', '主谋', 3, 2,
        '如果恶魔死于处决（本会结束游戏），再多进行一天。如果之后有玩家被处决，该玩家的阵营落败。', FALSE, FALSE, FALSE, 1,
        NULL, NULL, NULL, NULL, 'https://wiki.bloodontheclocktower.com/Mastermind', TRUE, 210),
       ('BAD_MOON_RISING', 'ZOMBUUL', '僵怖', 4, 2,
        '每个夜晚*，如果今天无人死亡，选择一名玩家：他死亡。你第一次死亡时，你存活但被当作死亡。', FALSE, TRUE, FALSE, 1,
        NULL, 8, NULL, '如果今天白天没有人死亡，让僵怖选择一名玩家。标记那名玩家死亡。',
        'https://wiki.bloodontheclocktower.com/Zombuul', TRUE, 220),
       ('BAD_MOON_RISING', 'PUKKA', '普卡', 4, 2, '每个夜晚，选择一名玩家：他中毒。此前中毒的玩家死亡，然后恢复健康。', TRUE,
        TRUE, FALSE, 1, 6, 9, '让普卡选择一名玩家。标记那名玩家中毒。',
        '让普卡选择一名玩家。标记那名玩家中毒。上一个因普卡中毒的玩家死亡，随后恢复健康。',
        'https://wiki.bloodontheclocktower.com/Pukka', TRUE, 230),
       ('BAD_MOON_RISING', 'SHABALOTH', '沙巴洛斯', 4, 2,
        '每个夜晚*，选择两名玩家：他们死亡。你上一夜选择的一名已死亡玩家可能被反刍复活。', FALSE, TRUE, FALSE, 1, NULL, 10,
        NULL,
        '上一夜被沙巴洛斯选择且当前已死亡的玩家之一可能被反刍，如果被反刍，标记那名玩家被复活。让沙巴洛斯选择两名玩家。标记这两名玩家死亡。',
        'https://wiki.bloodontheclocktower.com/Shabaloth', TRUE, 240),
       ('BAD_MOON_RISING', 'PO', '珀', 4, 2,
        '每个夜晚*，你可以选择一名玩家：他死亡。如果你上一次选择的是无人，今晚选择三名玩家。', FALSE, TRUE, FALSE, 1, NULL,
        11, NULL, '珀可以选择一名玩家；或如果上一次他被唤醒时未做选择，让他选择三名玩家。标记这些玩家死亡。',
        'https://wiki.bloodontheclocktower.com/Po', TRUE, 250),
       ('SECTS_AND_VIOLETS', 'CLOCKMAKER', '钟表匠', 1, 1, '在你的首个夜晚，你会得知从恶魔到最近的爪牙之间相隔多少步。',
        TRUE, FALSE, FALSE, 1, 6, NULL, '给他展示数字手势来告诉他恶魔与爪牙之间最近的距离。', NULL,
        'https://wiki.bloodontheclocktower.com/Clockmaker', TRUE, 10),
       ('SECTS_AND_VIOLETS', 'DREAMER', '筑梦师', 1, 1,
        '每个夜晚，选择一名玩家（不能选择自己或旅行者）：你会得知一个善良角色和一个邪恶角色，其中一个是正确的。', TRUE, TRUE,
        FALSE, 1, 7, 13, '让筑梦师指向一名玩家。对他展示善良和邪恶的角色标记各一个，其中一个是属于该玩家的角色。',
        '让筑梦师指向一名玩家。对他展示善良和邪恶的角色标记各一个，其中一个是属于该玩家的角色。',
        'https://wiki.bloodontheclocktower.com/Dreamer', TRUE, 20),
       ('SECTS_AND_VIOLETS', 'SNAKECHARMER', '舞蛇人', 1, 1,
        '每个夜晚，选择一名存活玩家：被选中的恶魔与你交换角色与阵营，随后中毒。', TRUE, TRUE, FALSE, 1, 2, 2,
        '让舞蛇人选择一名玩家。如果舞蛇人选中了恶魔：展示“你是”信息标记和恶魔角色标记。用拇指向下代表他阵营变为邪恶。在魔典中交换舞蛇人和恶魔的角色标记。让原来的舞蛇人重新入睡。唤醒原来的恶魔。对老恶魔展示“你是”信息标记和舞蛇人角色标记，并用拇指向上代表他阵营变为善良。',
        '让舞蛇人选择一名玩家。如果舞蛇人选中了恶魔：展示“你是”信息标记和恶魔角色标记。用拇指向下代表他阵营变为邪恶。在魔典中交换舞蛇人和恶魔的角色标记。让原来的舞蛇人重新入睡。唤醒原来的恶魔。对老恶魔展示“你是”信息标记和舞蛇人角色标记，并用拇指向上代表他阵营变为善良。',
        'https://wiki.bloodontheclocktower.com/Snake_Charmer', TRUE, 30),
       ('SECTS_AND_VIOLETS', 'MATHEMATICIAN', '数学家', 1, 1,
        '每个夜晚，你会得知自黎明以来有多少名玩家的能力因其他角色的能力而异常运作。', TRUE, TRUE, FALSE, 1, 9, 19,
        '给他展示数字手势来告诉他在首个夜晚里有多少玩家的角色能力受他人影响而未正常生效。',
        '给他展示数字手势来告诉他从上个黎明到数学家醒来前有多少玩家的角色能力受他人影响而未正常生效。',
        'https://wiki.bloodontheclocktower.com/Mathematician', TRUE, 40),
       ('SECTS_AND_VIOLETS', 'FLOWERGIRL', '卖花女孩', 1, 1, '每个夜晚*，你会得知今天恶魔是否投票。', FALSE, TRUE, FALSE,
        1, NULL, 14, NULL, '对她点头或摇头来示意今天白天是否有恶魔投过票。',
        'https://wiki.bloodontheclocktower.com/Flowergirl', TRUE, 50),
       ('SECTS_AND_VIOLETS', 'TOWNCRIER', '城镇公告员', 1, 1, '每个夜晚*，你会得知今天是否有爪牙发起提名。', FALSE, TRUE,
        FALSE, 1, NULL, 15, NULL, '对他点头或摇头示意今天白天是否有爪牙发起过提名。',
        'https://wiki.bloodontheclocktower.com/Town_Crier', TRUE, 60),
       ('SECTS_AND_VIOLETS', 'ORACLE', '神谕者', 1, 1, '每个夜晚*，你会得知死亡玩家中有几名是邪恶的。', FALSE, TRUE,
        FALSE, 1, NULL, 16, NULL, '给他展示数字手势来告诉他当前已死亡的玩家中有多少玩家是邪恶的。',
        'https://wiki.bloodontheclocktower.com/Oracle', TRUE, 70),
       ('SECTS_AND_VIOLETS', 'SAVANT', '博学者', 1, 1, '每个白天，你可以私下拜访说书人以得知两件事：一真一假。', FALSE,
        FALSE, FALSE, 1, NULL, NULL, NULL, NULL, 'https://wiki.bloodontheclocktower.com/Savant', TRUE, 80),
       ('SECTS_AND_VIOLETS', 'SEAMSTRESS', '女裁缝', 1, 1,
        '每局游戏限一次，在夜晚选择除你以外的两名玩家：你会得知他们是否阵营相同。', TRUE, TRUE, FALSE, 1, 8, 17,
        '女裁缝可以选择除自己以外的两名玩家。如果她这么做了，对她点头或摇头示意这两名玩家是否为同一阵营，随后标记女裁缝失去能力。之后的夜晚无需再唤醒女裁缝。',
        '女裁缝可以选择除自己以外的两名玩家。如果她这么做了，对她点头或摇头示意这两名玩家是否为同一阵营，随后标记女裁缝失去能力。之后的夜晚无需再唤醒女裁缝。',
        'https://wiki.bloodontheclocktower.com/Seamstress', TRUE, 90),
       ('SECTS_AND_VIOLETS', 'PHILOSOPHER', '哲学家', 1, 1,
        '每局游戏限一次，在夜晚选择一个善良角色：获得其能力。如果该角色在场，他醉酒。', TRUE, TRUE, FALSE, 1, 1, 1,
        '哲学家可以选择一个善良角色。如果选择的角色不在场，将哲学家的角色标记替换成对应角色，并标记“是哲学家”，否则标记该角色对应的玩家醉酒。从现在开始，你需要以哲学家获得能力的那种角色的行动方式来唤醒哲学家。',
        '哲学家可以选择一个角色。如果选择的角色不在场，将哲学家的角色标记替换成对应角色，并标记“是哲学家”，否则标记该角色对应的玩家醉酒。从现在开始，你需要以哲学家获得能力的那种角色的行动方式来唤醒哲学家。',
        'https://wiki.bloodontheclocktower.com/Philosopher', TRUE, 100),
       ('SECTS_AND_VIOLETS', 'ARTIST', '艺术家', 1, 1, '每局游戏限一次，在白天私下向说书人询问任意是/否问题。', FALSE,
        FALSE, FALSE, 1, NULL, NULL, NULL, NULL, 'https://wiki.bloodontheclocktower.com/Artist', TRUE, 110),
       ('SECTS_AND_VIOLETS', 'JUGGLER', '杂耍艺人', 1, 1,
        '在你的首个白天，公开猜测最多五名玩家的角色。今晚，你会得知你猜对了几个。', FALSE, TRUE, FALSE, 1, NULL, 18, NULL,
        '给他展示数字手势来告诉他他当天白天猜测正确的次数。', 'https://wiki.bloodontheclocktower.com/Juggler', TRUE,
        120),
       ('SECTS_AND_VIOLETS', 'SAGE', '贤者', 1, 1, '如果恶魔杀死你，你会得知恶魔是两名玩家之一。', FALSE, TRUE, FALSE, 1,
        NULL, 12, NULL, '如果恶魔杀死了贤者，唤醒贤者并指向两名玩家，其中一名玩家是杀死他的恶魔。',
        'https://wiki.bloodontheclocktower.com/Sage', TRUE, 130),
       ('SECTS_AND_VIOLETS', 'MUTANT', '畸形秀演员', 2, 1, '如果你“疯狂”地表现得像自己是外来者，你可能被处决。', FALSE,
        FALSE, FALSE, 1, NULL, NULL, NULL, NULL, 'https://wiki.bloodontheclocktower.com/Mutant', TRUE, 140),
       ('SECTS_AND_VIOLETS', 'SWEETHEART', '心上人', 2, 1, '当你死亡时，一名玩家从此醉酒。', FALSE, TRUE, FALSE, 1, NULL,
        11, NULL, '如果心上人死亡，会有一名玩家立刻醉酒。如果你还没有让这件事情发生，那么现在为任意一位玩家放置醉酒标记。',
        'https://wiki.bloodontheclocktower.com/Sweetheart', TRUE, 150),
       ('SECTS_AND_VIOLETS', 'BARBER', '理发师', 2, 1,
        '如果你今天或今晚死亡，恶魔可以选择两名玩家（不能是另一名恶魔）交换角色。', FALSE, TRUE, FALSE, 1, NULL, 10, NULL,
        '如果理发师今天死亡了，唤醒恶魔并展示“该角色的效果对你生效”信息标记和理发师角色标记。如果恶魔选择了两名玩家，将这两名玩家分别独自唤醒。对他们展示“你是”信息标记和他们的新角色标记。',
        'https://wiki.bloodontheclocktower.com/Barber', TRUE, 160),
       ('SECTS_AND_VIOLETS', 'KLUTZ', '呆瓜', 2, 1,
        '当你得知自己死亡时，公开选择一名存活玩家：如果他是邪恶玩家，你的阵营落败。', FALSE, FALSE, FALSE, 1, NULL, NULL,
        NULL, NULL, 'https://wiki.bloodontheclocktower.com/Klutz', TRUE, 170),
       ('SECTS_AND_VIOLETS', 'EVILTWIN', '镜像双子', 3, 2,
        '你和一名对立阵营玩家互相知道彼此。如果善良玩家被处决，邪恶获胜。只要你们都存活，善良无法获胜。', TRUE, FALSE, FALSE,
        1, 3, NULL,
        '唤醒镜像双子和他的对立双子，让他们进行眼神接触。对镜像双子展示对立双子的角色标记，并对对立双子展示镜像双子的角色标记。',
        NULL, 'https://wiki.bloodontheclocktower.com/Evil_Twin', TRUE, 180),
       ('SECTS_AND_VIOLETS', 'WITCH', '女巫', 3, 2,
        '每个夜晚，选择一名玩家：如果他明天提名，他死亡。当只有三名玩家存活时，你失去此能力。', TRUE, TRUE, FALSE, 1, 4, 3,
        '让女巫选择一名玩家。标记那名玩家被诅咒。', '让女巫选择一名玩家。标记那名玩家被诅咒。',
        'https://wiki.bloodontheclocktower.com/Witch', TRUE, 190),
       ('SECTS_AND_VIOLETS', 'CERENOVUS', '洗脑师', 3, 2,
        '每个夜晚，选择一名玩家和一个善良角色：明天他必须“疯狂”地表现得像自己是该角色，否则可能被处决。', TRUE, TRUE, FALSE,
        1, 5, 4,
        '让洗脑师选择一名玩家和一个善良角色。标记那名玩家疯狂。让洗脑师重新入睡。唤醒洗脑师的目标。对这名玩家展示“该角色的能力对你生效”信息标记，洗脑师角色标记，该玩家需要疯狂证明的角色标记。',
        '让洗脑师选择一名玩家和一个善良角色。标记那名玩家疯狂。让洗脑师重新入睡。唤醒洗脑师的目标。对这名玩家展示“该角色的能力对你生效”信息标记，洗脑师角色标记，该玩家需要疯狂证明的角色标记。',
        'https://wiki.bloodontheclocktower.com/Cerenovus', TRUE, 200),
       ('SECTS_AND_VIOLETS', 'PITHAG', '麻脸巫婆', 3, 2,
        '每个夜晚*，选择一名玩家和一个角色；如果该角色不在场，他变成该角色。如果创造了恶魔，今晚死亡任意。', FALSE, TRUE,
        FALSE, 1, NULL, 5, NULL,
        '让麻脸巫婆选择一名玩家和一个角色。如果她选择的角色不在场：让麻脸巫婆重新入睡。唤醒她的目标玩家。对该玩家展示“你是”信息标记和他的新角色标记。',
        'https://wiki.bloodontheclocktower.com/Pit-Hag', TRUE, 210),
       ('SECTS_AND_VIOLETS', 'FANGGU', '方古', 4, 2,
        '每个夜晚*，选择一名玩家：他死亡。第一个被此能力杀死的外来者变成邪恶的方古，你改为死亡。[+1 外来者]', FALSE, TRUE,
        FALSE, 1, NULL, 6, NULL,
        '让方古选择一名玩家。标记那名玩家死亡。如果他选择了外来者，且“限一次”标记未放置在魔典中：用备用的方古角色标记替换那名外来者的角色标记。让方古重新入睡。唤醒方古的目标玩家。对该玩家展示“你是”信息标记和方古角色标记，并用拇指向下代表他阵营变为邪恶。将“限一次”标记放置在魔典中央。标记原本的方古玩家死亡，且他选择的玩家不会被标记为死亡。',
        'https://wiki.bloodontheclocktower.com/Fang_Gu', TRUE, 220),
       ('SECTS_AND_VIOLETS', 'VIGORMORTIS', '亡骨魔', 4, 2,
        '每个夜晚*，选择一名玩家：他死亡。你杀死的爪牙保留能力，并使一名邻近镇民中毒。[-1 外来者]', FALSE, TRUE, FALSE, 1,
        NULL, 9, NULL,
        '让亡骨魔选择一名玩家。标记那名玩家死亡。如果该玩家是爪牙，标记该玩家保留能力，并标记与该玩家邻近的镇民玩家之一中毒。',
        'https://wiki.bloodontheclocktower.com/Vigormortis', TRUE, 230),
       ('SECTS_AND_VIOLETS', 'NODASHII', '诺-达鲺', 4, 2, '每个夜晚*，选择一名玩家：他死亡。与你邻近的两名镇民中毒。',
        FALSE, TRUE, FALSE, 1, NULL, 7, NULL, '让诺-达鲺选择一名玩家。标记那名玩家死亡。',
        'https://wiki.bloodontheclocktower.com/No_Dashii', TRUE, 240),
       ('SECTS_AND_VIOLETS', 'VORTOX', '涡流', 4, 2,
        '每个夜晚*，选择一名玩家：他死亡。镇民能力得到错误信息。每个白天，如果无人被处决，邪恶获胜。', FALSE, TRUE, FALSE, 1,
        NULL, 8, NULL, '让涡流选择一名玩家。标记那名玩家死亡。', 'https://wiki.bloodontheclocktower.com/Vortox', TRUE,
        250);

DELETE
FROM clocktower_night_order
WHERE script_code IN ('TROUBLE_BREWING', 'BAD_MOON_RISING', 'SECTS_AND_VIOLETS');

INSERT INTO clocktower_night_order (script_code, role_code, night_type, order_no, sort_order, reminder_text)
VALUES ('TROUBLE_BREWING', 'POISONER', 1, 1, 1, '让投毒者选择一名玩家。标记那名玩家中毒。'),
       ('TROUBLE_BREWING', 'WASHERWOMAN', 1, 2, 2, '展示那个镇民角色标记。指向被你标记“镇民”和“错误”的两名玩家。'),
       ('TROUBLE_BREWING', 'LIBRARIAN', 1, 3, 3, '展示那个外来者角色标记。指向被你标记“外来者”和“错误”的两名玩家。'),
       ('TROUBLE_BREWING', 'INVESTIGATOR', 1, 4, 4, '展示那个爪牙角色标记。指向被你标记“爪牙”和“错误”的两名玩家。'),
       ('TROUBLE_BREWING', 'CHEF', 1, 5, 5, '给他展示数字手势来告诉他场上邻座邪恶玩家有多少对。'),
       ('TROUBLE_BREWING', 'EMPATH', 1, 6, 6, '给他展示数字手势来告诉他与他邻近的存活玩家有几人是邪恶的。'),
       ('TROUBLE_BREWING', 'FORTUNETELLER', 1, 7, 7,
        '让占卜师选择两名玩家。如果其中有恶魔或“干扰项”，点头示意，否则摇头。'),
       ('TROUBLE_BREWING', 'BUTLER', 1, 8, 8, '让管家选择一名玩家。标记那名玩家为他的主人。'),
       ('TROUBLE_BREWING', 'SPY', 1, 9, 9, '将魔典展示给间谍，他想看多久就看多久。'),
       ('TROUBLE_BREWING', 'POISONER', 2, 1, 1, '让投毒者选择一名玩家。标记那名玩家中毒。'),
       ('TROUBLE_BREWING', 'MONK', 2, 2, 2, '让僧侣选择除自己外的一名玩家。标记那名玩家被保护。'),
       ('TROUBLE_BREWING', 'SCARLETWOMAN', 2, 3, 3,
        '如果红唇女郎今天变成了小恶魔，对她展示“你是”信息标记，和小恶魔角色标记。'),
       ('TROUBLE_BREWING', 'IMP', 2, 4, 4,
        '让小恶魔选择一名玩家。标记那名玩家死亡。如果小恶魔选择了自己：用一个备用的小恶魔标记替换一个存活的爪牙角色标记。让原来的小恶魔重新入睡。唤醒新的小恶魔。对他展示“你是”信息标记，和小恶魔角色标记。'),
       ('TROUBLE_BREWING', 'RAVENKEEPER', 2, 5, 5,
        '如果守鸦人今晚死亡，唤醒他并让他选择一名玩家。对他展示那名玩家的角色标记。'),
       ('TROUBLE_BREWING', 'EMPATH', 2, 6, 6, '给他展示数字手势来告诉他与他邻近的存活玩家有几人是邪恶的。'),
       ('TROUBLE_BREWING', 'FORTUNETELLER', 2, 7, 7,
        '让占卜师选择两名玩家。如果其中有恶魔或“干扰项”，点头示意，否则摇头。'),
       ('TROUBLE_BREWING', 'UNDERTAKER', 2, 8, 8, '如果有玩家今天白天死于处决，唤醒送葬者并对他展示那名玩家的角色标记。'),
       ('TROUBLE_BREWING', 'BUTLER', 2, 9, 9, '让管家选择一名玩家。标记那名玩家为他的主人。'),
       ('TROUBLE_BREWING', 'SPY', 2, 10, 10, '将魔典展示给间谍，他想看多久就看多久。'),
       ('BAD_MOON_RISING', 'LUNATIC', 1, 1, 1,
        '如果有七名或更多玩家，唤醒疯子：展示“他们是你的爪牙”信息标记。指向任意对应数量的玩家。展示“这些角色不在场”信息标记。展示三个善良角色。让疯子重新入睡。唤醒恶魔。展示“你是”信息标记和恶魔角色标记。展示“这名玩家是”信息标记和疯子角色标记，然后指向疯子玩家。'),
       ('BAD_MOON_RISING', 'SAILOR', 1, 2, 2, '让水手选择一名存活玩家。标记那名玩家或水手醉酒。'),
       ('BAD_MOON_RISING', 'COURTIER', 1, 3, 3,
        '侍臣可以选择一个角色。如果他这么做了，标记侍臣失去能力，标记被选择的角色所对应的玩家醉酒。之后的夜晚无需再唤醒侍臣。'),
       ('BAD_MOON_RISING', 'GODFATHER', 1, 4, 4, '对他展示所有在场的外来者标记。'),
       ('BAD_MOON_RISING', 'DEVILSADVOCATE', 1, 5, 5, '让魔鬼代言人选择一名存活玩家。标记那名玩家处决不死。'),
       ('BAD_MOON_RISING', 'PUKKA', 1, 6, 6, '让普卡选择一名玩家。标记那名玩家中毒。'),
       ('BAD_MOON_RISING', 'GRANDMOTHER', 1, 7, 7, '指向她的孙子玩家，并展示该玩家的角色标记。'),
       ('BAD_MOON_RISING', 'CHAMBERMAID', 1, 8, 8,
        '让侍女选择除自己外的两名存活玩家。给她展示数字手势来告诉她这些玩家中有几人因自身能力被唤醒。'),
       ('BAD_MOON_RISING', 'SAILOR', 2, 1, 1, '让水手选择一名存活玩家。标记那名玩家或水手醉酒。'),
       ('BAD_MOON_RISING', 'COURTIER', 2, 2, 2,
        '侍臣可以选择一个角色。如果他这么做了，标记侍臣失去能力，标记被选择的角色所对应的玩家醉酒。之后的夜晚无需再唤醒侍臣。'),
       ('BAD_MOON_RISING', 'INNKEEPER', 2, 3, 3, '让旅店老板选择两名玩家。标记这两名玩家不会死亡，并标记其中一人醉酒。'),
       ('BAD_MOON_RISING', 'GAMBLER', 2, 4, 4, '让赌徒选择一名玩家和一个角色。如果赌徒猜错了，标记赌徒死亡。'),
       ('BAD_MOON_RISING', 'DEVILSADVOCATE', 2, 5, 5,
        '让魔鬼代言人选择一名存活玩家，不能是上一夜他选择过的玩家。标记那名玩家处决不死。'),
       ('BAD_MOON_RISING', 'LUNATIC', 2, 6, 6,
        '做任何需要做的事情来模拟一位恶魔的行动。让疯子重新入睡。唤醒恶魔。对恶魔展示疯子角色标记，并指向疯子玩家，随后是疯子的攻击目标。'),
       ('BAD_MOON_RISING', 'EXORCIST', 2, 7, 7,
        '让驱魔人选择一名玩家，不能是上一夜他选择过的玩家。让驱魔人重新入睡。如果驱魔人选中了恶魔：唤醒恶魔。展示“该角色的能力对你生效”信息标记和驱魔人角色标记。指向驱魔人玩家。'),
       ('BAD_MOON_RISING', 'ZOMBUUL', 2, 8, 8, '如果今天白天没有人死亡，让僵怖选择一名玩家。标记那名玩家死亡。'),
       ('BAD_MOON_RISING', 'PUKKA', 2, 9, 9,
        '让普卡选择一名玩家。标记那名玩家中毒。上一个因普卡中毒的玩家死亡，随后恢复健康。'),
       ('BAD_MOON_RISING', 'SHABALOTH', 2, 10, 10,
        '上一夜被沙巴洛斯选择且当前已死亡的玩家之一可能被反刍，如果被反刍，标记那名玩家被复活。让沙巴洛斯选择两名玩家。标记这两名玩家死亡。'),
       ('BAD_MOON_RISING', 'PO', 2, 11, 11,
        '珀可以选择一名玩家；或如果上一次他被唤醒时未做选择，让他选择三名玩家。标记这些玩家死亡。'),
       ('BAD_MOON_RISING', 'ASSASSIN', 2, 12, 12,
        '刺客可以选择一名玩家。如果他这么做了，标记那名玩家死亡，且刺客失去能力，之后的夜晚无需再唤醒刺客。'),
       ('BAD_MOON_RISING', 'GODFATHER', 2, 13, 13, '如果有外来者在今天白天死亡，让教父选择一名玩家。标记那名玩家死亡。'),
       ('BAD_MOON_RISING', 'GOSSIP', 2, 14, 14,
        '如果白天的声明为真，会有一名玩家死亡，并由说书人来选择一名玩家，标记该玩家死亡。'),
       ('BAD_MOON_RISING', 'PROFESSOR', 2, 15, 15,
        '教授可以选择一名死亡玩家。如果他这么做了，标记教授失去能力，然后如果那名玩家是镇民，标记那名玩家被复活。之后的夜晚无需再唤醒教授。'),
       ('BAD_MOON_RISING', 'TINKER', 2, 16, 16, '修补匠可能会死亡。如果说书人选择让修补匠死亡，放置死亡标记。'),
       ('BAD_MOON_RISING', 'MOONCHILD', 2, 17, 17,
        '如果月之子在白天触发了死亡能力并选择了一名善良玩家，该玩家死亡。标记那名玩家死亡。'),
       ('BAD_MOON_RISING', 'GRANDMOTHER', 2, 18, 18, '如果孙子被恶魔杀死，祖母也会一同死亡。标记祖母死亡。'),
       ('BAD_MOON_RISING', 'CHAMBERMAID', 2, 19, 19,
        '让侍女选择除自己外的两名存活玩家。给她展示数字手势来告诉她这些玩家中有几人因自身能力被唤醒。'),
       ('SECTS_AND_VIOLETS', 'PHILOSOPHER', 1, 1, 1,
        '哲学家可以选择一个善良角色。如果选择的角色不在场，将哲学家的角色标记替换成对应角色，并标记“是哲学家”，否则标记该角色对应的玩家醉酒。从现在开始，你需要以哲学家获得能力的那种角色的行动方式来唤醒哲学家。'),
       ('SECTS_AND_VIOLETS', 'SNAKECHARMER', 1, 2, 2,
        '让舞蛇人选择一名玩家。如果舞蛇人选中了恶魔：展示“你是”信息标记和恶魔角色标记。用拇指向下代表他阵营变为邪恶。在魔典中交换舞蛇人和恶魔的角色标记。让原来的舞蛇人重新入睡。唤醒原来的恶魔。对老恶魔展示“你是”信息标记和舞蛇人角色标记，并用拇指向上代表他阵营变为善良。'),
       ('SECTS_AND_VIOLETS', 'EVILTWIN', 1, 3, 3,
        '唤醒镜像双子和他的对立双子，让他们进行眼神接触。对镜像双子展示对立双子的角色标记，并对对立双子展示镜像双子的角色标记。'),
       ('SECTS_AND_VIOLETS', 'WITCH', 1, 4, 4, '让女巫选择一名玩家。标记那名玩家被诅咒。'),
       ('SECTS_AND_VIOLETS', 'CERENOVUS', 1, 5, 5,
        '让洗脑师选择一名玩家和一个善良角色。标记那名玩家疯狂。让洗脑师重新入睡。唤醒洗脑师的目标。对这名玩家展示“该角色的能力对你生效”信息标记，洗脑师角色标记，该玩家需要疯狂证明的角色标记。'),
       ('SECTS_AND_VIOLETS', 'CLOCKMAKER', 1, 6, 6, '给他展示数字手势来告诉他恶魔与爪牙之间最近的距离。'),
       ('SECTS_AND_VIOLETS', 'DREAMER', 1, 7, 7,
        '让筑梦师指向一名玩家。对他展示善良和邪恶的角色标记各一个，其中一个是属于该玩家的角色。'),
       ('SECTS_AND_VIOLETS', 'SEAMSTRESS', 1, 8, 8,
        '女裁缝可以选择除自己以外的两名玩家。如果她这么做了，对她点头或摇头示意这两名玩家是否为同一阵营，随后标记女裁缝失去能力。之后的夜晚无需再唤醒女裁缝。'),
       ('SECTS_AND_VIOLETS', 'MATHEMATICIAN', 1, 9, 9,
        '给他展示数字手势来告诉他在首个夜晚里有多少玩家的角色能力受他人影响而未正常生效。'),
       ('SECTS_AND_VIOLETS', 'PHILOSOPHER', 2, 1, 1,
        '哲学家可以选择一个角色。如果选择的角色不在场，将哲学家的角色标记替换成对应角色，并标记“是哲学家”，否则标记该角色对应的玩家醉酒。从现在开始，你需要以哲学家获得能力的那种角色的行动方式来唤醒哲学家。'),
       ('SECTS_AND_VIOLETS', 'SNAKECHARMER', 2, 2, 2,
        '让舞蛇人选择一名玩家。如果舞蛇人选中了恶魔：展示“你是”信息标记和恶魔角色标记。用拇指向下代表他阵营变为邪恶。在魔典中交换舞蛇人和恶魔的角色标记。让原来的舞蛇人重新入睡。唤醒原来的恶魔。对老恶魔展示“你是”信息标记和舞蛇人角色标记，并用拇指向上代表他阵营变为善良。'),
       ('SECTS_AND_VIOLETS', 'WITCH', 2, 3, 3, '让女巫选择一名玩家。标记那名玩家被诅咒。'),
       ('SECTS_AND_VIOLETS', 'CERENOVUS', 2, 4, 4,
        '让洗脑师选择一名玩家和一个善良角色。标记那名玩家疯狂。让洗脑师重新入睡。唤醒洗脑师的目标。对这名玩家展示“该角色的能力对你生效”信息标记，洗脑师角色标记，该玩家需要疯狂证明的角色标记。'),
       ('SECTS_AND_VIOLETS', 'PITHAG', 2, 5, 5,
        '让麻脸巫婆选择一名玩家和一个角色。如果她选择的角色不在场：让麻脸巫婆重新入睡。唤醒她的目标玩家。对该玩家展示“你是”信息标记和他的新角色标记。'),
       ('SECTS_AND_VIOLETS', 'FANGGU', 2, 6, 6,
        '让方古选择一名玩家。标记那名玩家死亡。如果他选择了外来者，且“限一次”标记未放置在魔典中：用备用的方古角色标记替换那名外来者的角色标记。让方古重新入睡。唤醒方古的目标玩家。对该玩家展示“你是”信息标记和方古角色标记，并用拇指向下代表他阵营变为邪恶。将“限一次”标记放置在魔典中央。标记原本的方古玩家死亡，且他选择的玩家不会被标记为死亡。'),
       ('SECTS_AND_VIOLETS', 'NODASHII', 2, 7, 7, '让诺-达鲺选择一名玩家。标记那名玩家死亡。'),
       ('SECTS_AND_VIOLETS', 'VORTOX', 2, 8, 8, '让涡流选择一名玩家。标记那名玩家死亡。'),
       ('SECTS_AND_VIOLETS', 'VIGORMORTIS', 2, 9, 9,
        '让亡骨魔选择一名玩家。标记那名玩家死亡。如果该玩家是爪牙，标记该玩家保留能力，并标记与该玩家邻近的镇民玩家之一中毒。'),
       ('SECTS_AND_VIOLETS', 'BARBER', 2, 10, 10,
        '如果理发师今天死亡了，唤醒恶魔并展示“该角色的效果对你生效”信息标记和理发师角色标记。如果恶魔选择了两名玩家，将这两名玩家分别独自唤醒。对他们展示“你是”信息标记和他们的新角色标记。'),
       ('SECTS_AND_VIOLETS', 'SWEETHEART', 2, 11, 11,
        '如果心上人死亡，会有一名玩家立刻醉酒。如果你还没有让这件事情发生，那么现在为任意一位玩家放置醉酒标记。'),
       ('SECTS_AND_VIOLETS', 'SAGE', 2, 12, 12,
        '如果恶魔杀死了贤者，唤醒贤者并指向两名玩家，其中一名玩家是杀死他的恶魔。'),
       ('SECTS_AND_VIOLETS', 'DREAMER', 2, 13, 13,
        '让筑梦师指向一名玩家。对他展示善良和邪恶的角色标记各一个，其中一个是属于该玩家的角色。'),
       ('SECTS_AND_VIOLETS', 'FLOWERGIRL', 2, 14, 14, '对她点头或摇头来示意今天白天是否有恶魔投过票。'),
       ('SECTS_AND_VIOLETS', 'TOWNCRIER', 2, 15, 15, '对他点头或摇头示意今天白天是否有爪牙发起过提名。'),
       ('SECTS_AND_VIOLETS', 'ORACLE', 2, 16, 16, '给他展示数字手势来告诉他当前已死亡的玩家中有多少玩家是邪恶的。'),
       ('SECTS_AND_VIOLETS', 'SEAMSTRESS', 2, 17, 17,
        '女裁缝可以选择除自己以外的两名玩家。如果她这么做了，对她点头或摇头示意这两名玩家是否为同一阵营，随后标记女裁缝失去能力。之后的夜晚无需再唤醒女裁缝。'),
       ('SECTS_AND_VIOLETS', 'JUGGLER', 2, 18, 18, '给他展示数字手势来告诉他他当天白天猜测正确的次数。'),
       ('SECTS_AND_VIOLETS', 'MATHEMATICIAN', 2, 19, 19,
        '给他展示数字手势来告诉他从上个黎明到数学家醒来前有多少玩家的角色能力受他人影响而未正常生效。');
