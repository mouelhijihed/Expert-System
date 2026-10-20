import java.util.*;
import java.util.function.Consumer;

public class ForwardChaining {

    public static boolean prove(ExpertSystem system, String goal) {
        return prove(system, goal, System.out::println);
    }

    public static boolean prove(ExpertSystem system, String goal, Consumer<String> log) {
        Set<String> facts = new LinkedHashSet<>(system.getFacts());
        log.accept("=== FORWARD CHAINING ===");
        log.accept("Goal: " + goal);

        if (facts.contains(goal)) {
            log.accept("Goal already exists in the facts.");
            return true;
        }

        boolean added;
        do {
            added = false;
            for (Rule rule : system.getRules()) {
                if (facts.contains(rule.getConclusion())) continue;
                if (facts.containsAll(rule.getConditions())) {
                    log.accept("Rule applied: " + rule);
                    facts.add(rule.getConclusion());
                    log.accept("New fact deduced: " + rule.getConclusion());
                    added = true;
                    if (facts.contains(goal)) {
                        log.accept("Goal successfully proved!");
                        return true;
                    }
                }
            }
        } while (added);

        log.accept("Goal cannot be proved.");
        return false;
    }
}