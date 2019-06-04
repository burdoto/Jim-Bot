package de.kaleidox.jim;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import de.kaleidox.JimBot;
import de.kaleidox.util.files.FileProvider;
import de.kaleidox.util.interfaces.Initializable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.role.UserRoleRemoveEvent;
import org.javacord.api.listener.server.role.UserRoleRemoveListener;
import org.javacord.core.util.logging.LoggerUtil;

import static java.time.Instant.now;

public enum AssignmentManager implements UserRoleRemoveListener, Initializable, Closeable {
    INSTANCE;

    private static final Logger log = LoggerUtil.getLogger(AssignmentManager.class);
    private static final File FILE = FileProvider.getFile("data/timed.json");

    private Map<String, List<TemporaryAssignment>> assignments = new ConcurrentHashMap<>();

    @Override
    public void init() throws IOException {
        JsonNode data = new ObjectMapper().readTree(FILE);

        if (data == null) return;

        Iterator<String> serverIds = data.fieldNames();
        serverIds.forEachRemaining(sId -> {
            JsonNode serverNode = data.get(sId);

            Iterator<String> userIds = serverNode.fieldNames();
            userIds.forEachRemaining(uId -> {
                JsonNode userNode = serverNode.get(uId);

                JsonNode assignmentArray = userNode.get("assignments");
                for (JsonNode assignmentNode : assignmentArray) {
                    assignments.compute(sId + ":" + uId, (k, v) -> {
                        if (v == null) v = new ArrayList<>();
                        TemporaryAssignment assignment = new TemporaryAssignment(assignmentNode);
                        if (assignment.isOutdated()) {
                            assignment.remove(TemporaryAssignment.RemovalStatus.OUTDATED);
                            log.debug("Skipped loading assignment of role [" + assignment.getRoleid() + "] for user " +
                                    "[" + assignment.getUserId() + "]: The assignment is outdated.");
                        } else {
                            v.add(assignment);
                            log.debug("Loaded assignment of role [" + assignment.getRoleid() + "] " +
                                    "for user [" + assignment.getUserId() + "]");
                        }
                        return v;
                    });
                }
            });
        });

        assignments.values()
                .stream()
                .flatMap(Collection::stream)
                .forEach(TemporaryAssignment::scheduleRemoval);

        JimBot.API.getThreadPool()
                .getScheduler()
                .scheduleAtFixedRate(this::cleanup, 10, 10, TimeUnit.MINUTES);
    }

    @Override
    public void close() throws IOException {
        cleanup();

        storeData();
    }

    public void storeData() throws IOException {
        ObjectNode node = JsonNodeFactory.instance.objectNode();

        assignments.forEach((key, value) -> {
            String[] split = key.split(":");

            ObjectNode serverNode = node.has(split[0])
                    ? (ObjectNode) node.get(split[0])
                    : node.putObject(split[0]);

            ObjectNode userNode = serverNode.has(split[1])
                    ? (ObjectNode) serverNode.get(split[1])
                    : serverNode.putObject(split[1]);

            ArrayNode assignmentsArray = userNode.has("assignments")
                    ? (ArrayNode) userNode.get("assignments")
                    : userNode.putArray("assignments");

            for (TemporaryAssignment temporaryAssignment : value)
                assignmentsArray.add(temporaryAssignment.toJson());
        });

        FileWriter fileWriter = new FileWriter(FILE);
        fileWriter.write(node.toString());
        fileWriter.close();
    }

    public Instant removeRoleAfter(User target, Role targetRole, String timeString, boolean temporaryRole) throws ParseException {
        Instant now = now();

        long secondsDuration = extractTime(timeString);
        Instant targetInstant = now.plusSeconds(secondsDuration);

        assignments.compute(targetRole.getServer().getId() + ":" + target.getId(), (k, v) -> {
            if (v == null) v = new ArrayList<>();
            TemporaryAssignment assignment = new TemporaryAssignment(target, targetRole, targetInstant, temporaryRole);
            v.add(assignment);
            assignment.scheduleRemoval();
            return v;
        });

        return targetInstant;
    }

    @Override
    public void onUserRoleRemove(UserRoleRemoveEvent event) {
        long role = event.getRole().getId();

        assignments.forEach((key, list) -> {
            for (TemporaryAssignment assignment : list) {
                if (assignment.getRoleid() == role) {
                    assignment.remove(TemporaryAssignment.RemovalStatus.CANCELLED);
                }
            }
        });
    }

    private void cleanup() {
        class RemovalPair {
            final String key;
            final TemporaryAssignment assignment;

            RemovalPair(String key, TemporaryAssignment assignment) {
                this.key = key;
                this.assignment = assignment;
            }
        }

        List<RemovalPair> removalPairs = new ArrayList<>();

        assignments.forEach((key, list) -> {
            for (TemporaryAssignment assignment : list) {
                if (assignment.cleanupable)
                    removalPairs.add(new RemovalPair(key, assignment));
            }
        });

        removalPairs.forEach(pair -> assignments.compute(pair.key, (k, v) -> {
            if (v == null) return null;
            v.remove(pair.assignment);
            if (v.size() == 0) return null;
            return v;
        }));

        for (String key : assignments.keySet())
            assignments.compute(key, (k, v) -> {
                if (v == null || v.size() == 0)
                    return null;
                return v;
            });

        if (removalPairs.size() > 0)
            log.info("Cleaned up [" + removalPairs.size() + "] old assignments!");
    }

    public static long extractTime(String timeString) throws ParseException {
        int weeks = 0, days = 0, hours = 0, minutes = 0, seconds = 0;

        char[] chars = timeString.toCharArray();
        int c = 0;

        for (int i = 0; i < chars.length; i++) {
            char p = chars[i];

            if (Character.isDigit(p)) {
                c = c * 10;
                c += Integer.parseInt(String.valueOf(p));
            } else //noinspection StatementWithEmptyBody
                if (Character.isWhitespace(p)) {
                    // skip character
                } else {
                    switch (p) {
                        case 'w':
                            if (weeks != 0)
                                throw new ParseException("Illegal duplication: WEEKS field [" + timeString + "]", i);
                            weeks = c;
                            break;
                        case 'd':
                            if (days != 0)
                                throw new ParseException("Illegal duplication: DAYS field [" + timeString + "]", i);
                            days = c;
                            break;
                        case 'h':
                            if (hours != 0)
                                throw new ParseException("Illegal duplication: HOURS field [" + timeString + "]", i);
                            hours = c;
                            break;
                        case 'm':
                            if (minutes != 0)
                                throw new ParseException("Illegal duplication: MINUTES field [" + timeString + "]", i);
                            minutes = c;
                            break;
                        case 's':
                            if (seconds != 0)
                                throw new ParseException("Illegal duplication: SECONDS field [" + timeString + "]", i);
                            seconds = c;
                            break;
                        default:
                            throw new ParseException("Unknown identifier: [" + p + "]", i);
                    }

                    c = 0;
                }
        }

        if (c != 0 && minutes == 0)
            minutes = c;

        return (weeks * 7 * 24 * 60 * 60)
                + (days * 24 * 60 * 60)
                + (hours * 60 * 60)
                + (minutes * 60)
                + seconds;
    }
}
