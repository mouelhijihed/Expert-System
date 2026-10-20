import java.util.*;
import java.util.function.Consumer;

public class BackwardChaining {

    public static boolean prove(ExpertSystem system, String goal) {
        return prove(system, goal, System.out::println);
    }

    public static boolean prove(ExpertSystem system, String goal, Consumer<String> log) {
        log.accept("=== BACKWARD CHAINING ===");
        log.accept("Goal to prove: " + goal);

        boolean result = proveGoal(system, goal, new HashSet<>(), 0, log);

        log.accept(result
                ? "Goal successfully proved: " + goal
                : "Goal cannot be proved: " + goal);
        return result;
    }

    private static boolean proveGoal(ExpertSystem system, String goal,
                                     Set<String> activeGoals, int depth,
                                     Consumer<String> log) {
        String indent = "  ".repeat(depth);

        if (system.getFacts().contains(goal)) {
            log.accept(indent + "Fact found: " + goal);
            return true;
        }

        if (!activeGoals.add(goal)) {
            log.accept(indent + "Cycle detected: " + goal);
            return false;
        }

        log.accept(indent + "Trying to prove: " + goal);

        for (Rule rule : system.getRules()) {
            if (!rule.getConclusion().equals(goal)) continue;

            log.accept(indent + "Checking rule: " + rule);

            boolean all = true;
            for (String condition : rule.getConditions()) {
                if (!proveGoal(system, condition, activeGoals, depth + 1, log)) {
                    all = false;
                    break;
                }
            }

            if (all) {
                log.accept(indent + "Rule proves: " + goal);
                activeGoals.remove(goal);
                return true;
            }
        }

        log.accept(indent + "Failed to prove: " + goal);
        activeGoals.remove(goal);
        return false;
    }
}