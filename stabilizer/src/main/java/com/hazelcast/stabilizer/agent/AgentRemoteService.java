package com.hazelcast.stabilizer.agent;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.stabilizer.TestRecipe;
import com.hazelcast.stabilizer.Utils;
import com.hazelcast.stabilizer.tests.Failure;
import com.hazelcast.stabilizer.tests.Workout;
import com.hazelcast.stabilizer.worker.testcommands.TestCommand;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AgentRemoteService {
    public static final String SERVICE_SPAWN_WORKERS = "spawnWorkers";
    public static final String SERVICE_INIT_WORKOUT = "initWorkout";
    public static final String SERVICE_CLEAN_WORKERS_HOME = "cleanWorkersHome";
    public static final String SERVICE_TERMINATE_WORKERS = "terminateWorkers";
    public static final String SERVICE_EXECUTE_ALL_WORKERS = "executeOnAllWorkers";
    public static final String SERVICE_EXECUTE_SINGLE_WORKER = "executeOnSingleWorker";
    public static final String SERVICE_ECHO = "echo";
    public static final String SERVICE_PREPARE_FOR_TEST = "prepareForTest";
    public static final String SERVICE_GET_FAILURES = "failures";

    private final static ILogger log = Logger.getLogger(AgentRemoteService.class);

    private Agent agent;
    private ServerSocket serverSocket;
    private final Executor executor = Executors.newFixedThreadPool(20);

    public AgentRemoteService(Agent agent) {
        this.agent = agent;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(9000, 0, InetAddress.getByName(Utils.getHostAddress()));
        log.info("Started on: " + serverSocket.getInetAddress());

        new Thread() {
            public void run() {
                for (; ; ) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        log.info("Accepted client request from: " + clientSocket.getRemoteSocketAddress());
                        executor.execute(new ClientSocketTask(clientSocket));
                    } catch (IOException e) {
                        log.severe(e);
                    }
                }
            }
        }.start();
    }

    private class ClientSocketTask implements Runnable {
        private final Socket clientSocket;

        private ClientSocketTask(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                out.flush();

                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                String service = (String) in.readObject();

                Object result = null;
                try {
                    if (SERVICE_GET_FAILURES.equals(service)) {
                        result = getFailures();
                    } else if (SERVICE_SPAWN_WORKERS.equals(service)) {
                        WorkerJvmSettings settings = (WorkerJvmSettings) in.readObject();
                        spawnWorkers(settings);
                    } else if (SERVICE_INIT_WORKOUT.equals(service)) {
                        Workout workout = (Workout) in.readObject();
                        initWorkout(workout);
                    } else if (SERVICE_CLEAN_WORKERS_HOME.equals(service)) {
                        cleanWorkersHome();
                    } else if (SERVICE_TERMINATE_WORKERS.equals(service)) {
                        terminateWorkers();
                    } else if (SERVICE_EXECUTE_ALL_WORKERS.equals(service)) {
                        TestCommand testCommand = (TestCommand) in.readObject();
                        WorkerJvmManager workerJvmManager = agent.getWorkerJvmManager();
                        workerJvmManager.executeOnAllWorkers(testCommand);
                    }else if (SERVICE_EXECUTE_SINGLE_WORKER.equals(service)) {
                        TestCommand testCommand = (TestCommand) in.readObject();
                        WorkerJvmManager workerJvmManager = agent.getWorkerJvmManager();
                        workerJvmManager.executeOnSingleWorker(testCommand);
                    } else if (SERVICE_ECHO.equals(service)) {
                        String msg = (String) in.readObject();
                        echo(msg);
                    } else if (SERVICE_PREPARE_FOR_TEST.equals(service)) {
                        TestRecipe recipe = (TestRecipe) in.readObject();
                        prepareForTest(recipe);
                    } else {
                        throw new RuntimeException("Unknown service:" + service);
                    }
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    result = e;
                }

                out.writeObject(result);
                out.flush();
                clientSocket.close();
            } catch (Exception e) {
                log.severe(e);
            }
        }
    }

    public ArrayList<Failure> getFailures() {
        ArrayList<Failure> failures = new ArrayList<Failure>();
        agent.getWorkerJvmFailureMonitor().drainFailures(failures);
        return failures;
    }

    public void spawnWorkers(WorkerJvmSettings settings) throws Exception {
        try {
            agent.getWorkerJvmManager().spawn(settings);
        } catch (Exception e) {
            log.severe("Failed to spawn workers from settings:" + settings, e);
            throw e;
        }
    }

    public void initWorkout(Workout workout) throws Exception {
        try {
            agent.initWorkout(workout, null);
        } catch (Exception e) {
            log.severe("Failed to init workout: " + workout, e);
            throw e;
        }
    }

    public void cleanWorkersHome() throws Exception {
        try {
            agent.getWorkerJvmManager().cleanWorkersHome();
        } catch (Exception e) {
            log.severe("Failed to clean workers home", e);
            throw e;
        }
    }

    public void terminateWorkers() throws Exception {
        try {
            agent.getWorkerJvmManager().terminateWorkers();
        } catch (Exception e) {
            log.severe("Failed to terminate workers", e);
            throw e;
        }
    }

    public void echo(String msg) throws Exception {
        try {
            agent.echo(msg);
        } catch (Exception e) {
            log.severe("Failed to echo", e);
            throw e;
        }
    }

    public void prepareForTest(TestRecipe testRecipe) throws Exception {
        try {
            agent.setTestRecipe(testRecipe);
        } catch (Exception e) {
            log.severe("Failed to prepareForTest for recipe:" + testRecipe, e);
            throw e;
        }
    }
}