package top.egon.mario.im.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.facade.ImException;
import top.egon.mario.im.facade.dto.command.MintWsTicketCommand;
import top.egon.mario.im.facade.dto.view.WsTicketView;
import top.egon.mario.im.po.ImWsTicketPo;
import top.egon.mario.im.po.enums.ImWsTicketStatus;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImWsTicketRepository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

@Service
public class ImTicketService {

    private static final Duration TICKET_TTL = Duration.ofMinutes(2);
    private static final int TOKEN_BYTES = 32;

    private final ImWsTicketRepository ticketRepository;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public ImTicketService(ImWsTicketRepository ticketRepository, ObjectMapper objectMapper) {
        this.ticketRepository = ticketRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WsTicketView mint(MintWsTicketCommand command) {
        ImPrincipal principal = requirePrincipal(command);
        String rawToken = randomToken();
        Instant expiresAt = Instant.now().plus(TICKET_TTL);

        ImWsTicketPo ticket = new ImWsTicketPo();
        ticket.setTokenHash(hash(rawToken));
        ticket.setUserId(principal.userId());
        ticket.setRolesJson(principalPayload(principal));
        ticket.setExpiresAt(expiresAt);
        ticket.setStatus(ImWsTicketStatus.ACTIVE);
        ticket.setMetadataJson("{}");
        ticketRepository.saveAndFlush(ticket);

        return new WsTicketView(rawToken, expiresAt);
    }

    @Transactional(noRollbackFor = ImException.class)
    public ImPrincipal consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ImException("IM_WS_TICKET_INVALID");
        }
        ImWsTicketPo ticket = ticketRepository.findLockedByTokenHashAndDeletedFalse(hash(rawToken.trim()))
                .orElseThrow(() -> new ImException("IM_WS_TICKET_INVALID"));
        if (ImWsTicketStatus.CONSUMED.equals(ticket.getStatus()) || ticket.getConsumedAt() != null) {
            throw new ImException("IM_WS_TICKET_CONSUMED");
        }
        Instant now = Instant.now();
        if (ImWsTicketStatus.EXPIRED.equals(ticket.getStatus()) || !ticket.getExpiresAt().isAfter(now)) {
            ticket.setStatus(ImWsTicketStatus.EXPIRED);
            ticketRepository.saveAndFlush(ticket);
            throw new ImException("IM_WS_TICKET_EXPIRED");
        }
        if (!ImWsTicketStatus.ACTIVE.equals(ticket.getStatus())) {
            throw new ImException("IM_WS_TICKET_INVALID");
        }

        PrincipalPayload payload = principalPayload(ticket.getRolesJson());
        ticket.setStatus(ImWsTicketStatus.CONSUMED);
        ticket.setConsumedAt(now);
        ticketRepository.saveAndFlush(ticket);
        return new ImPrincipal(ticket.getUserId(), payload.roleCodes(), payload.contextType(), payload.attributes());
    }

    private ImPrincipal requirePrincipal(MintWsTicketCommand command) {
        if (command == null || command.principal() == null) {
            throw new ImException("IM_WS_TICKET_PRINCIPAL_REQUIRED");
        }
        return command.principal();
    }

    private String principalPayload(ImPrincipal principal) {
        try {
            return objectMapper.writeValueAsString(new PrincipalPayload(
                    principal.roleCodes(),
                    principal.contextType(),
                    principal.attributes()
            ));
        } catch (JsonProcessingException ex) {
            throw new ImException("IM_WS_TICKET_PAYLOAD_INVALID");
        }
    }

    private PrincipalPayload principalPayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, PrincipalPayload.class);
        } catch (JsonProcessingException ex) {
            throw new ImException("IM_WS_TICKET_PAYLOAD_INVALID");
        }
    }

    private String randomToken() {
        byte[] token = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is required", ex);
        }
    }

    private record PrincipalPayload(Set<String> roleCodes, String contextType, Map<String, String> attributes) {
    }
}
