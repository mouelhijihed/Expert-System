
import java.util.List;

public class Rule {

    private List<String> conditions;
    private String conclusion;

    public Rule(List<String> conditions, String conclusion) {
        this.conditions = conditions;
        this.conclusion = conclusion;
    }

    public List<String> getConditions() {
        return conditions;
    }

    public String getConclusion() {
        return conclusion;
    }

    @Override
    public String toString() {
        return "IF " + String.join(" AND ", conditions)
                + " THEN " + conclusion;
    }
}
