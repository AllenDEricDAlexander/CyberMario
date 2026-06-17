package top.egon.mario.clocktower.script.web;

import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.script.dto.response.ClocktowerJinxRuleResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerNightOrderResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerRoleResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerScriptResponse;
import top.egon.mario.clocktower.script.dto.response.ClocktowerTermResponse;
import top.egon.mario.clocktower.script.service.ClocktowerScriptService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clocktower")
@Validated
public class ClocktowerScriptController {

    private final ClocktowerScriptService scriptService;

    @GetMapping("/scripts")
    public Mono<List<ClocktowerScriptResponse>> scripts() {
        return Mono.fromSupplier(scriptService::listScripts);
    }

    @GetMapping("/scripts/{scriptCode}")
    public Mono<ClocktowerScriptResponse> script(@PathVariable ClocktowerScriptCode scriptCode) {
        return Mono.fromSupplier(() -> scriptService.getScript(scriptCode));
    }

    @GetMapping("/scripts/{scriptCode}/roles")
    public Mono<List<ClocktowerRoleResponse>> roles(@PathVariable ClocktowerScriptCode scriptCode,
                                                    @RequestParam(required = false) String roleType,
                                                    @RequestParam(required = false) Boolean enabled) {
        return Mono.fromSupplier(() -> scriptService.listRoles(scriptCode, roleType, enabled));
    }

    @GetMapping("/scripts/{scriptCode}/night-order")
    public Mono<List<ClocktowerNightOrderResponse>> nightOrder(@PathVariable ClocktowerScriptCode scriptCode,
                                                               @RequestParam(required = false) String nightType) {
        return Mono.fromSupplier(() -> scriptService.nightOrder(scriptCode, nightType));
    }

    @GetMapping("/terms")
    public Mono<List<ClocktowerTermResponse>> terms(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String category) {
        return Mono.fromSupplier(() -> scriptService.terms(keyword, category));
    }

    @GetMapping("/jinx-rules")
    public Mono<List<ClocktowerJinxRuleResponse>> jinxRules(@RequestParam(required = false) String roleCode,
                                                            @RequestParam(required = false) String severity) {
        return Mono.fromSupplier(() -> scriptService.jinxRules(roleCode, severity));
    }
}
