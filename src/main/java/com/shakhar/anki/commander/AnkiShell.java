package com.shakhar.anki.commander;

import de.adesso.anki.AnkiConnector;
import de.adesso.anki.Vehicle;
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
import java.util.List;

public class AnkiShell implements Command, Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnkiShell.class);

    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback callback;
    private Thread thread;
    private Terminal terminal;
    private AnkiConnector ankiConnector;

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
                try {
                    if (args.length >= 3)
                        ankiConnector = new AnkiConnector(args[1], Integer.parseInt(args[2]));
                    else
                        ankiConnector = new AnkiConnector("localhost", 5000);
                } catch (IOException e) {
                    e.printStackTrace(terminal.writer());
                }
                break;
            case "search":
                List<Vehicle> vehicles = ankiConnector.findVehicles();
                if (vehicles.isEmpty())
                    write("No Vehicles Found.");
                else {
                    write("Found " + vehicles.size() + " vehicles.");
                    for (Vehicle vehicle : vehicles) {
                        write("Vehicle " + vehicle.getAddress());
                    }
                }
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
}
