package com.swift.radiohome;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

/**
 * Runs one shell command through the head unit's own adbd on 127.0.0.1:5555 (no auth, root shell), speaking the ADB
 * wire protocol. Only fixed commands built in this app go through it. Fails with an IOException when ADB is closed.
 */
final class LocalAdb {
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            WRTE = 0x45545257, CLSE = 0x45534c43;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private LocalAdb() {
    }

    static String shell(String command) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            DataInputStream in = new DataInputStream(socket.getInputStream());

            send(out, CNXN, 0x01000000, 4096, "host::\0");
            int[] head = read(in, null);
            if (head[0] == AUTH) throw new IOException("adb asks for authorisation");
            if (head[0] != CNXN) throw new IOException("unexpected reply");

            send(out, OPEN, 1, 0, "shell:" + command + "\0");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            while (true) {
                head = read(in, output);
                if (head[0] == WRTE) {
                    send(out, OKAY, 1, head[1], "");
                } else if (head[0] == CLSE) {
                    return new String(output.toByteArray(), UTF8);
                }
            }
        } finally {
            socket.close();
        }
    }

    private static void send(OutputStream out, int command, int arg0, int arg1, String payload) throws IOException {
        byte[] data = payload.getBytes(UTF8);
        int sum = 0;
        for (byte b : data) sum += b & 0xff;
        ByteBuffer head = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        head.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~command);
        out.write(head.array());
        out.write(data);
        out.flush();
    }

    /** Reads one message; a WRTE payload is appended to {@code sink}. Returns {command, arg0}. */
    private static int[] read(DataInputStream in, ByteArrayOutputStream sink) throws IOException {
        byte[] raw = new byte[24];
        in.readFully(raw);
        ByteBuffer head = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int command = head.getInt(), arg0 = head.getInt();
        head.getInt();
        int length = head.getInt();
        byte[] data = new byte[length];
        in.readFully(data);
        if (command == WRTE && sink != null) sink.write(data, 0, data.length);
        return new int[]{command, arg0};
    }
}
