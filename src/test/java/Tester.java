import org.junit.Test;
import org.rhea_core.Stream;

import mqtt_eval.MqttEvaluationStrategy;
import rx_eval.RxjavaEvaluationStrategy;
import test_data.TestData;
import test_data.TestInfo;
import test_data.utilities.Colors;

/**
 * @author Orestis Melkonian
 */
public class Tester {

    @Test
    public void mqtt_eval() {
        Stream.evaluationStrategy =
                new MqttEvaluationStrategy(new RxjavaEvaluationStrategy(), "myclientname");

        for (TestInfo test : TestData.tests()) {
            System.out.print(test.name + ": ");
            if (test.equality())
                Colors.println(Colors.GREEN, "pass");
            else
                Colors.println(Colors.RED, "fail");
        }
    }
}

