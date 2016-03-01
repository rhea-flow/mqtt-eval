import mqtt_eval.MqttEvaluationStrategy;
import org.junit.Test;
import org.rhea_core.Stream;
import org.rhea_core.util.functions.Actions;
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
        Stream.setEvaluationStrategy(new MqttEvaluationStrategy(RxjavaEvaluationStrategy::new));

        for (TestInfo test : TestData.tests()) {
//            System.out.print(test.name + ": ");
            String name = test.name;
            Stream<Integer> s1 = test.s1;
            Stream<Integer> s2 = test.s2;
            Stream.sequenceEqual(s1, s2).subscribe(i -> {
                if (i)
                    Colors.println(Colors.GREEN, name + ": pass");
                else
                    Colors.println(Colors.RED, name + ": fail");
            });

        }
    }
}

