package org.apache.flink.streaming.api.operators;

import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class is entirely new and added to the storm sources. The StreamMonitor is attached to the
 * processor nodes that are used in the Stream API. It is called for every incoming and outgoing
 * event and keeps track of the data characteristics (like tuple width, selectivities, etc.) As no
 * shutdown hooks can be reveiced here when shutting down the topology, the measurement has a
 * defined length It starts when the first tuple arrives. At the end, the data characteristics are
 * written into the logs per operator.
 */
public class StreamMonitor implements Serializable {

    private static final long serialVersionUID = 1L;

    private final HashMap<String, Object> description;
    private boolean initialized;
    private boolean observationMade;
    private long startTime;
    private int inputCounter;
    private int outputCounter;
    private final long duration = 30_000_000_000L; // 30 seconds, starting after first call
    private boolean localMode;
    // private final BaseProcessor<?> processor;
    // private final Map<String, Object> topoConf;
    private final ArrayList<Double> joinSelectivities;
    private final ArrayList<Integer> windowLengths;
    private Logger logger;
    // private MongoCollection<JSONObject> mongoCollection;

    public StreamMonitor(HashMap<String, Object> description) {
        // this.topoConf = Utils.readStormConfig();
        if (description == null) {
            this.description = new HashMap<>();
        } else {
            this.description = description;
        }

        this.logger = LoggerFactory.getLogger("observation");
        this.description.put("tupleWidthIn", -2);
        this.description.put("tupleWidthOut", -2);
        this.initialized = false;
        this.observationMade = false;
        // this.processor = processor;
        this.joinSelectivities = new ArrayList<>();
        this.windowLengths = new ArrayList<>();
        //        if (this.processor instanceof JoinProcessor || this.processor instanceof
        // FilterProcessor) {
        //            this.description.put("realSelectivity", 0.0);
        //        }
    }

    public <T> void reportInput(T input) {
        if (!initialized) {
            initialized = true;

            // initialize loggers
            //  if (((ArrayList<String>) topoConf.get("nimbus.seeds")).get(0).equals("localhost")) {
            this.localMode = true;
            //  } else {
            //      this.localMode = false;
            //      MongoClient mongoClient = new MongoClient(((ArrayList<String>)
            // topoConf.get("nimbus.seeds")).get(0), 27017);
            //       mongoCollection =
            // mongoClient.getDatabase("dsps").getCollection("query_observations",
            // JSONObject.class);
            //   }
            this.startTime = System.nanoTime();
            description.put("tupleWidthIn", getTupleSize(input));
            description.put("tupleWidthOut", -1); // not any observation yet
        }
        this.inputCounter++;
        checkIfObservationEnd();
    }

    public <T> void reportOutput(T output) {
        description.put("tupleWidthOut", getTupleSize(output));
        this.outputCounter++;
        checkIfObservationEnd();
    }

    //    public void reportJoinSelectivity(int size1, int size2, int joinPartners) {
    //        if (size1 != 0 && size2 != 0) {
    //            this.joinSelectivities.add((double) joinPartners / (double) (size1 * size2));
    //        }
    //    }

    //    public <T> void reportWindowLength(T state) {
    //        int length;
    //        if (state instanceof Long || state instanceof Double || state instanceof Integer) {
    //            length = 1;
    //        } else {
    //            try {
    //                length = ((ArrayList<Values>) state).size();
    //            } catch (ClassCastException e1) {
    //                Pair<Object, Object> p = (Pair<Object, Object>) state;
    //                length = ((ArrayList<Values>) p.getSecond()).size();
    //            }
    //        }
    //        this.windowLengths.add(length);
    //    }

    private void checkIfObservationEnd() {
        if (!observationMade) {
            long elapsedTime = System.nanoTime() - this.startTime;
            if (elapsedTime > duration) {
                description.put(
                        "outputRate", ((double) this.outputCounter * 1e9 / (double) elapsedTime));
                description.put(
                        "inputRate", ((double) this.inputCounter * 1e9 / (double) elapsedTime));
                //                if (this.processor instanceof JoinProcessor) {
                //                    Double average =
                // this.joinSelectivities.stream().mapToDouble(val -> val).average().orElse(0.0);
                //                    description.put("realSelectivity", average);
                //                } else if (this.processor instanceof FilterProcessor) {
                //                    description.put("realSelectivity", ((double)
                // this.outputCounter / (double) this.inputCounter));
                //                } else if (this.processor instanceof AggregateProcessor) {
                //                    double averageWindowLength =
                // this.windowLengths.stream().mapToDouble(val -> val).average().orElse(0.0);
                //                    description.put("realSelectivity", ((double) 1 /
                // averageWindowLength));
                //                }
                JSONObject json = new JSONObject();
                json.putAll(description);

                // Log data characteristics either locally ore in database
                // if (this.description.get("id") != null && this.localMode) {
                logger.info(json.toJSONString());
                //                } else if (this.description.get("id") != null && !this.localMode)
                // {
                //                    mongoCollection.insertOne(json);
                //                }
                observationMade = true;
            }
        }
    }

    private <T> int getTupleSize(T input) {
        //        DataTuple dt = input.getValue();
        //        Tuple3<Integer, Integer, Integer> getNumTupleDataTypes =
        // dt.getNumTupleDataTypes();
        int size = 3;
        return size;

        //        if (input instanceof Integer || input instanceof String || input instanceof Double
        // || input instanceof Long) {
        //            return 1;
        //        }
        //
        //        int size = 0;
        //        try {
        //            Values v = (Values) input;
        //            size = v.size();
        //        } catch (ClassCastException e1) {
        //            try {
        //                TupleImpl v = (TupleImpl) input;
        //                size = v.size();
        //            } catch (ClassCastException e2) {
        //                Pair<Object, Object> p = (Pair<Object, Object>) input;
        //                try {
        //                    Values v = (Values) p.getSecond();
        //                    if (p.getSecond() != null) {
        //                        size = v.size();
        //                    }
        //                } catch (ClassCastException e3) {
        //                    if (p.getSecond() instanceof Integer || p.getSecond() instanceof
        // String || p.getSecond()
        //                            instanceof Double || p.getSecond() instanceof Long) {
        //                        size = 1;
        //                    } else {
        //                        ArrayList<Values> l = (ArrayList<Values>) p.getSecond();
        //                        size = l.get(0).size();
        //                    }
        //                }
        //            }
        //        }
        //        return size;
    }
}
