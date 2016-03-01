package mqtt_eval;

import mqtt_eval.mqqt_graph.MqttEdge;
import mqtt_eval.mqqt_graph.MqttGraph;
import mqtt_eval.mqqt_graph.MqttNode;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.rhea_core.Stream;
import org.rhea_core.evaluation.EvaluationStrategy;
import org.rhea_core.internal.expressions.MultipleInputExpr;
import org.rhea_core.internal.expressions.NoInputExpr;
import org.rhea_core.internal.expressions.SingleInputExpr;
import org.rhea_core.internal.expressions.Transformer;
import org.rhea_core.internal.expressions.creation.FromSource;
import org.rhea_core.internal.graph.FlowGraph;
import org.rhea_core.internal.output.MultipleOutput;
import org.rhea_core.internal.output.Output;
import org.rhea_core.internal.output.SinkOutput;
import org.rhea_core.util.functions.Func0;
import remote_execution.Broker;
import remote_execution.RemoteExecution;
import remote_execution.StreamTask;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Orestis Melkonian
 */
public class MqttEvaluationStrategy implements EvaluationStrategy {

    final RemoteExecution executor = new RemoteExecution(); // TODO add machines
    Func0<EvaluationStrategy> evaluationStrategy;
    Broker broker // default public broker from Eclipse
//            = new Broker("m2m.eclipse.org", 1883);
//            = new Broker("tcp://178.128.109.227", 1884);
            = new Broker("localhost", 1884);
//            = new Broker("tcp://orestis-desktop", 1884);
    boolean brokerSet = true;

    /**
     * Constructors
     */
    public MqttEvaluationStrategy(Func0<EvaluationStrategy> evaluationStrategy) {
        this.evaluationStrategy = evaluationStrategy;
        this.brokerSet = false;
    }

    public MqttEvaluationStrategy(Func0<EvaluationStrategy> evaluationStrategy, Broker broker) {
        this.evaluationStrategy = evaluationStrategy;
        this.broker = broker;
    }

    public MqttEvaluationStrategy(Func0<EvaluationStrategy> evaluationStrategy, Broker broker, String nodePrefix) {
        this(evaluationStrategy, broker);
        this.nodePrefix = nodePrefix;
    }

    /**
     * Generators
     */
    String nodePrefix = "t";
    int topicCounter = 0, nodeCounter = 0;
    private String newName() {
        return nodePrefix + "_" + Integer.toString(nodeCounter++);
    }
    public MqttTopic newTopic() {
        return new MqttTopic(nodePrefix + "/" + Integer.toString(topicCounter++));
    }

    /**
     * Evaluation
     */
    private void execute(Queue<StreamTask> tasks) {
        Queue<MqttTask> wrappers = new LinkedList<>(tasks.stream().map(t -> new MqttTask(t, broker.toString(), newName())).collect(Collectors.toList()));
        executor.submit(wrappers);
    }

    @Override
    public <T> void evaluate(Stream<T> stream, Output output) {
        FlowGraph flow = stream.getGraph();
        MqttGraph graph = new MqttGraph(flow, this::newTopic);
        Queue<StreamTask> tasks = new LinkedList<>();

        // Add task to setup broker
        if (brokerSet)
            executor.executeOn(new MqttBrokerTask(broker), broker.getIp());

        // Run output node first
        MqttTopic result = newTopic();
        tasks.add(new StreamTask(evaluationStrategy, Stream.from(result), output, new ArrayList<>()));

        // Then run each graph vertex as an individual node (reverse BFS)
        Set<MqttNode> checked = new HashSet<>();
        Stack<MqttNode> stack = new Stack<>();
        for (MqttNode root : graph.getRoots())
            new BreadthFirstIterator<>(graph, root).forEachRemaining(stack::push);
        while (!stack.empty()) {
            MqttNode toExecute = stack.pop();
            if (checked.contains(toExecute)) continue;

            Set<MqttEdge> inputs = graph.incomingEdgesOf(toExecute);
            Transformer transformer = toExecute.getTransformer();

            FlowGraph innerGraph = new FlowGraph();
            if (transformer instanceof NoInputExpr) {
                assert inputs.size() == 0;
                // 0 input
                innerGraph.addConnectVertex(transformer);
            } else if (transformer instanceof SingleInputExpr) {
                assert inputs.size() == 1;
                // 1 input
                MqttTopic input = inputs.iterator().next().getTopic();
                Transformer toAdd = new FromSource<>(input.clone());
                innerGraph.addConnectVertex(toAdd);
                innerGraph.attach(transformer);
            } else if (transformer instanceof MultipleInputExpr) {
                assert inputs.size() > 1;
                // N inputs
                innerGraph.setConnectNodes(inputs.stream()
                        .map(edge -> new FromSource(edge.getTopic().clone()))
                        .collect(Collectors.toList()));
                innerGraph.attachMulti(transformer);
            }

            // Set outputs according to graph connections
            Set<MqttEdge> outputs = graph.outgoingEdgesOf(toExecute);
            List<Output> list = new ArrayList<>();
            if (transformer == graph.toConnect)
                list.add(new SinkOutput<>(result.clone()));
            list.addAll(outputs.stream()
                    .map(MqttEdge::getTopic)
                    .map((Function<MqttTopic, SinkOutput<Object>>) (sink) -> {
                        return new SinkOutput(sink);
                    })
                    .collect(Collectors.toList()));
            Output outputToExecute = (list.size() == 1) ? list.get(0) : new MultipleOutput(list);

            // Schedule for execution
            tasks.add(new StreamTask(evaluationStrategy, new Stream(innerGraph), outputToExecute, new ArrayList<>()));

            checked.add(toExecute);
        }

        // Submit the tasks for execution
        execute(tasks);
    }
}
