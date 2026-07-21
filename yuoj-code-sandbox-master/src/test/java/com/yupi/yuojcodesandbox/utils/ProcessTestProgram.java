package com.yupi.yuojcodesandbox.utils;

import java.util.Arrays;

public class ProcessTestProgram {

    public static void main(String[] args) throws Exception {
        if ("sleep".equals(args[0])) {
            Thread.sleep(30000L);
            return;
        }
        if ("flood".equals(args[0])) {
            byte[] output = new byte[4096];
            Arrays.fill(output, (byte) 'x');
            while (true) {
                System.out.write(output);
                System.out.flush();
            }
        }
    }
}
