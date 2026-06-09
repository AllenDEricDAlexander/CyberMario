package top.egon.mario.agent.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ToolMonitorInterceptor extends ToolInterceptor {
    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        long startTime = System.currentTimeMillis();
        try {
            ToolCallResponse response = handler.call(request);
            log.info("工具 [{}] 执行成功，耗时: {}ms", request.getToolName(), System.currentTimeMillis() - startTime);
            return response;
        } catch (Exception e) {
            log.error("工具 [{}] 执行失败，耗时: {}ms", request.getToolName(), System.currentTimeMillis() - startTime, e);
            return ToolCallResponse.error(request.getToolCallId(), request.getToolName(), e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "ToolErrorInterceptor";
    }
}