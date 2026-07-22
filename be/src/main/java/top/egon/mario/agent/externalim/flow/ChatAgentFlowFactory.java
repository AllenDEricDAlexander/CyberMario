package top.egon.mario.agent.externalim.flow;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import reactor.core.publisher.Flux;
import top.egon.mario.agent.externalim.model.ChatInvocation;

public interface ChatAgentFlowFactory {

    Flux<NodeOutput> stream(ChatInvocation invocation, ReactAgent agent,
                            RunnableConfig config, String guardGroupWindow,
                            String requestId, String traceId);
}
