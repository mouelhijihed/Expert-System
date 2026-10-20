
import java.util.*;

public class ExpertSystem {

    private final Set<String> facts = new LinkedHashSet<>();
    private final List<Rule> rules = new ArrayList<>();

    public ExpertSystem() {

    }

    public void displayKnowledgeBase() {
        System.out.println("\n=== FACTS ===");

        if (facts.isEmpty()) {
            System.out.println("No facts.");
        } else {
            for (String fact : facts) {
                System.out.println("- " + fact);
            }
        }

        System.out.println("\n=== RULES ===");

        if (rules.isEmpty()) {
            System.out.println("No rules.");
        } else {
            for (int i = 0; i < rules.size(); i++) {
                System.out.println(
                        "R" + (i + 1) + ": " + rules.get(i)
                );
            }
        }
    }

    public boolean addFact(String fact) {
        fact = normalize(fact);

        if (fact.isEmpty()) {
            return false;
        }

        return facts.add(fact);
    }

    public boolean removeFact(String fact) {
        return facts.remove(normalize(fact));
    }

    public boolean addRule(
            List<String> conditions,
            String conclusion) {

        conclusion = normalize(conclusion);

        if (conclusion.isEmpty() || conditions.isEmpty()) {
            return false;
        }

        List<String> cleanConditions = new ArrayList<>();

        for (String condition : conditions) {
            String clean = normalize(condition);

            if (clean.isEmpty()) {
                return false;
            }

            cleanConditions.add(clean);
        }

        rules.add(new Rule(cleanConditions, conclusion));
        return true;
    }

    public boolean removeRule(int ruleNumber) {
        if (ruleNumber < 1 || ruleNumber > rules.size()) {
            return false;
        }

        rules.remove(ruleNumber - 1);
        return true;
    }

    public Set<String> getFacts() {
        return facts;
    }

    public List<Rule> getRules() {
        return rules;
    }
    public void save(java.nio.file.Path path) throws java.io.IOException {
        List<String> lines = new ArrayList<>();
        for (String f : facts) lines.add("FACT|" + f);
        for (Rule r : rules) lines.add("RULE|" + String.join(",", r.getConditions()) + "|" + r.getConclusion());
        java.nio.file.Files.write(path, lines);
    }

    public void load(java.nio.file.Path path) throws java.io.IOException {
        facts.clear();
        rules.clear();
        for (String line : java.nio.file.Files.readAllLines(path)) {
            String[] p = line.split("\\|");
            if (p[0].equals("FACT") && p.length == 2) addFact(p[1]);
            else if (p[0].equals("RULE") && p.length == 3) addRule(Arrays.asList(p[1].split(",")), p[2]);
        }
    }
    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
