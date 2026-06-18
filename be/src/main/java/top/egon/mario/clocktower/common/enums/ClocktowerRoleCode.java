package top.egon.mario.clocktower.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import top.egon.mario.common.enums.CodedEnum;

import java.util.Map;

/**
 * Official Blood on the Clocktower role code with Chinese display name.
 */
@Getter
public enum ClocktowerRoleCode implements CodedEnum {
    CHEF(1, "厨师"),
    EMPATH(2, "共情者"),
    MONK(3, "僧侣"),
    POISONER(4, "投毒者"),
    IMP(5, "小恶魔"),
    WASHERWOMAN(6, "洗衣妇"),
    LIBRARIAN(7, "图书管理员"),
    INVESTIGATOR(8, "调查员"),
    FORTUNETELLER(9, "占卜师"),
    UNDERTAKER(10, "送葬者"),
    RAVENKEEPER(11, "守鸦人"),
    VIRGIN(12, "贞洁者"),
    SLAYER(13, "猎手"),
    SOLDIER(14, "士兵"),
    MAYOR(15, "镇长"),
    BUTLER(16, "管家"),
    DRUNK(17, "酒鬼"),
    RECLUSE(18, "陌客"),
    SAINT(19, "圣徒"),
    SPY(20, "间谍"),
    SCARLETWOMAN(21, "红唇女郎"),
    BARON(22, "男爵"),
    GRANDMOTHER(23, "祖母"),
    SAILOR(24, "水手"),
    CHAMBERMAID(25, "侍女"),
    EXORCIST(26, "驱魔人"),
    INNKEEPER(27, "旅店老板"),
    GAMBLER(28, "赌徒"),
    GOSSIP(29, "造谣者"),
    COURTIER(30, "侍臣"),
    PROFESSOR(31, "教授"),
    MINSTREL(32, "吟游诗人"),
    TEALADY(33, "茶艺师"),
    PACIFIST(34, "和平主义者"),
    FOOL(35, "弄臣"),
    GOON(36, "莽夫"),
    LUNATIC(37, "疯子"),
    TINKER(38, "修补匠"),
    MOONCHILD(39, "月之子"),
    GODFATHER(40, "教父"),
    DEVILSADVOCATE(41, "魔鬼代言人"),
    ASSASSIN(42, "刺客"),
    MASTERMIND(43, "主谋"),
    ZOMBUUL(44, "僵怖"),
    PUKKA(45, "普卡"),
    SHABALOTH(46, "沙巴洛斯"),
    PO(47, "珀"),
    CLOCKMAKER(48, "钟表匠"),
    DREAMER(49, "筑梦师"),
    SNAKECHARMER(50, "舞蛇人"),
    MATHEMATICIAN(51, "数学家"),
    FLOWERGIRL(52, "卖花女孩"),
    TOWNCRIER(53, "城镇公告员"),
    ORACLE(54, "神谕者"),
    SAVANT(55, "博学者"),
    SEAMSTRESS(56, "女裁缝"),
    PHILOSOPHER(57, "哲学家"),
    ARTIST(58, "艺术家"),
    JUGGLER(59, "杂耍艺人"),
    SAGE(60, "贤者"),
    MUTANT(61, "畸形秀演员"),
    SWEETHEART(62, "心上人"),
    BARBER(63, "理发师"),
    KLUTZ(64, "呆瓜"),
    EVILTWIN(65, "镜像双子"),
    WITCH(66, "女巫"),
    CERENOVUS(67, "洗脑师"),
    PITHAG(68, "麻脸巫婆"),
    FANGGU(69, "方古"),
    VIGORMORTIS(70, "亡骨魔"),
    NODASHII(71, "诺-达鲺"),
    VORTOX(72, "涡流");

    private final int code;
    private final String desc;

    ClocktowerRoleCode(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Map<String, Object> toJson() {
        return ClocktowerEnumJsonSupport.toJson(this);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ClocktowerRoleCode fromJson(Object input) {
        return ClocktowerEnumJsonSupport.fromJson(ClocktowerRoleCode.class, input);
    }
}
