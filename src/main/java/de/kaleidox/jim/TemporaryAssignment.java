package de.kaleidox.jim;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import de.kaleidox.JimBot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;
import org.intellij.lang.annotations.MagicConstant;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.core.util.logging.LoggerUtil;

import static java.time.Instant.now;

public class TemporaryAssignment {
    private final static Logger log = LoggerUtil.getLogger(TemporaryAssignment.class);

    private final long userId;
    private final Instant removeAt;
    private final long roleid;
    private final boolean temporaryRole;
    private ScheduledFuture<?> scheduledRemoval;
    boolean cleanupable = false;

    public TemporaryAssignment(JsonNode data) {
        this.userId = data.get("userid").asLong();
        this.removeAt = Instant.parse(data.get("removeat").asText());
        this.roleid = data.get("roleid").asLong();
        this.temporaryRole = data.path("temporaryrole").asBoolean(false);
    }

    public TemporaryAssignment(User target, Role targetRole, Instant targetInstant, boolean temporaryRole) {
        this.userId = target.getId();
        this.removeAt = targetInstant;
        this.roleid = targetRole.getId();
        this.temporaryRole = temporaryRole;
    }

    public boolean isOutdated() {
        Role role = JimBot.API.getRoleById(roleid).orElse(null);

        if (role == null) return true;

        return JimBot.API.getUserById(userId)
                .thenApply(user -> {
                    Server server = role.getServer();

                    return server.getRoles(user)
                            .stream()
                            .noneMatch(role::equals);
                })
                .join();
    }

    public long getUserId() {
        return userId;
    }

    public Instant getRemoveAt() {
        return removeAt;
    }

    public long getRoleid() {
        return roleid;
    }

    public boolean isTemporaryRole() {
        return temporaryRole;
    }

    public void scheduleRemoval() {
        Instant now = now();
        Instant removeAt = getRemoveAt();

        if (removeAt.isBefore(now))
            remove(RemovalStatus.EXPIRED);
        else {
            long until = now.until(removeAt, ChronoUnit.SECONDS);

            this.scheduledRemoval = JimBot.API.getThreadPool()
                    .getScheduler()
                    .schedule(() -> remove(RemovalStatus.EXPIRED), until, TimeUnit.SECONDS);
        }
    }

    public synchronized void remove(@MagicConstant(valuesFromClass = RemovalStatus.class) int status) {
        if (cleanupable) return;

        Role role = JimBot.API.getRoleById(roleid).orElse(null);

        if (role == null) {
            log.error("Unable to remove assignment from user [" + userId + "]: " +
                    "Role [" + roleid + "] was not found");
            return;
        }

        switch (status) {
            case RemovalStatus.EXPIRED:
                log.info("Assignment of role [" + roleid + "] for user [" + userId + "] expired!");

                break;
            case RemovalStatus.CANCELLED:
                scheduledRemoval.cancel(true);
                log.info("Assignment of role [" + roleid + "] for user [" + userId + "] " +
                        "was manually cancelled! Taking no further actions.");

                break;
            case RemovalStatus.OUTDATED:
                log.info("Assignment of role [" + roleid + "] for user [" + userId + "] " +
                        "was removed while bot was offline! Taking no further actions.");

                break;
        }

        JimBot.API.getUserById(userId)
                .thenCompose(user -> {
                    Server server = role.getServer();

                    server.removeRoleFromUser(user, role)
                            .join();

                    if (temporaryRole && server.getMembers()
                            .stream()
                            .flatMap(usr -> server.getRoles(usr).stream())
                            .noneMatch(role::equals)) {
                        log.info("Role [" + roleid + "] was a temporary role and was deleted, " +
                                "since there was no user left with that role");
                        return role.delete();
                    }

                    return CompletableFuture.completedFuture(null);
                })
                .join();

        cleanupable = true;
    }

    public JsonNode toJson() {
        ObjectNode me = JsonNodeFactory.instance.objectNode();

        me.put("userid", userId);
        me.put("removeat", removeAt.toString());
        me.put("roleid", roleid);
        me.put("temporaryrole", temporaryRole);

        return me;
    }

    public static class RemovalStatus {
        public static final int EXPIRED = 0;
        public static final int CANCELLED = 1;
        public static final int OUTDATED = 2;
    }
}
