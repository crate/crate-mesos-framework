package io.crate.frameworks.mesos;

import java.io.*;

public class StreamRedirect extends Thread {

    InputStream inputStream;
    PrintStream printStream;

    StreamRedirect(InputStream is, PrintStream type) {
        this.inputStream = is;
        this.printStream = type;
    }

    public void run() {
        try {
            InputStreamReader streamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(streamReader);
            String line = null;
            while ((line = bufferedReader.readLine()) != null)
                printStream.println(line);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
