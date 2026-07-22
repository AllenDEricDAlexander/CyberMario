package top.egon.mario.agent.externalim.guard;

public interface ChatGuardModel {

    ChatGuardResult evaluate(ChatGuardModelInput input);
}
