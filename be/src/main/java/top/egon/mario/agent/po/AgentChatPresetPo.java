package top.egon.mario.agent.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.common.entity.BaseAuditablePo;

/**
 * Saved agent debug preset created from the frontend debug workspace.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_chat_preset")
public class AgentChatPresetPo extends BaseAuditablePo {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "model_config_json")
    private String modelConfigJson;

    @Column(name = "model_options_json")
    private String modelOptionsJson;

    @Column(name = "system_prompt", nullable = false)
    private String systemPrompt;

    @Column(name = "tool_config_json")
    private String toolConfigJson;

    @Column(name = "agent_options_json")
    private String agentOptionsJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

}
