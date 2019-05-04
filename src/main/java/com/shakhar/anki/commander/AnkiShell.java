package com.shakhar.anki.commander;

import de.adesso.anki.AnkiConnector;
import de.adesso.anki.Vehicle;
import de.adesso.anki.messages.SdkModeMessage;
import de.adesso.anki.messages.SetSpeedMessage;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.command.Command;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnkiShell implements Command, Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnkiShell.class);

    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback callback;
    private Thread thread;
    private Terminal terminal;
    private AnkiConnector ankiConnector;

    private Map<String, Vehicle> vehicleMap;
    private List<String> controlList;

    @Override
    public void setInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void setErrorStream(OutputStream err) {
        this.err = err;
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.callback = callback;
    }

    @Override
    public void start(Environment env) throws IOException {
        thread = new Thread(this);
        thread.start();
    }

    @Override
    public void destroy() throws Exception {
        if (terminal != null)
            terminal.close();
        thread.interrupt();
    }

    @Override
    public void run() {
        try {
            terminal = TerminalBuilder.builder().system(false).streams(in, out).build();
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
            String line;
            while ((line = reader.readLine("anki>")) != null && handleInput(line));
        } catch (IOException e) {
            LOGGER.error("IOException thrown", e);
        } catch (UserInterruptException e) {
            LOGGER.error("UserInterruptException thrown", e);
        } finally {
            callback.onExit(0);
        }
    }

    private boolean handleInput(String command) {
        String[] args = command.split("\\s+");
        switch (args[0]) {
            case "connect":
                handleConnect(args);
                break;
            case "scan":
                handleScan();
                break;
            case "control":
                handleControl(args);
                break;
            case "speed":
                handleSpeed(args);
                break;
            case "exit":
                return false;
            default:
                write("Unknown command");
        }
        return true;
    }

    private void write(String s) {
        terminal.writer().println(s);
        terminal.writer().flush();
    }

    private void handleConnect(String[] args) {
        try {
            if (args.length >= 3)
                ankiConnector = new AnkiConnector(args[1], Integer.parseInt(args[2]));
            else
                ankiConnector = new AnkiConnector("localhost", 5000);
        } catch (IOException e) {
            e.printStackTrace(terminal.writer());
        }
        vehicleMap = new HashMap<>();
        controlList = new ArrayList<>();
    }

    private void handleScan() {
        vehicleMap.clear();
        List<Vehicle> vehicles = ankiConnector.findVehicles();
        if (vehicles.isEmpty())
            write("No Vehicles Found.");
        else {
            write("Found " + vehicles.size() + " vehicle(s):");
            for (Vehicle vehicle : vehicles) {
                vehicleMap.put(vehicle.getAddress(), vehicle);
                write(vehicle.getAddress() + ": " + vehicle.getAdvertisement());
            }
        }
    }

    private void handleControl(String[] args) {
        controlList.clear();
        for (int i = 1; i < args.length; i++)
            controlList.add(args[i]);
    }

    private void handleSpeed(String[] args) {
        int speed = Integer.parseInt(args[1]);
        int acceleration = Integer.parseInt(args[2]);
        for (String address: controlList) {
            Vehicle vehicle = vehicleMap.get(address);
            vehicle.connect();
            vehicle.sendMessage(new SdkModeMessage());
            vehicle.sendMessage(new SetSpeedMessage(speed, acceleration));
            vehicle.disconnect();
        }
    }
}
