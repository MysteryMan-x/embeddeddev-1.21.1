package com.mysteryman.embeddeddev.mcu;

/** Decodes base MCS-51 machine code, using the instruction address for jump targets. */
public final class Mcs51Disassembler {
    public record Instruction(int length, String text) {}

    private Mcs51Disassembler() {}

    public static Instruction decode(byte[] bytes, int offset, int address) {
        int opcode = bytes[offset] & 0xff;
        int first = operand(bytes, offset + 1);
        int second = operand(bytes, offset + 2);
        if ((opcode & 0x0f) == 1) {
            int target = ((address + 2) & 0xf800) | ((opcode & 0xe0) << 3) | first;
            String mnemonic = (opcode & 0x10) == 0 ? "AJMP " : "ACALL ";
            return new Instruction(2, mnemonic + word(target));
        }

        int low = opcode & 15;
        int high = opcode >>> 4;
        if (low >= 6) {
            String register = low < 8 ? "@R" + (low & 1) : "R" + (low & 7);
            return switch (high) {
                case 0x0 -> new Instruction(1, "INC " + register);
                case 0x1 -> new Instruction(1, "DEC " + register);
                case 0x2 -> new Instruction(1, "ADD A," + register);
                case 0x3 -> new Instruction(1, "ADDC A," + register);
                case 0x4 -> new Instruction(1, "ORL A," + register);
                case 0x5 -> new Instruction(1, "ANL A," + register);
                case 0x6 -> new Instruction(1, "XRL A," + register);
                case 0x7 -> new Instruction(2, "MOV " + register + "," + immediate(first));
                case 0x8 -> new Instruction(2, "MOV " + direct(first) + "," + register);
                case 0x9 -> new Instruction(1, "SUBB A," + register);
                case 0xa -> new Instruction(2, "MOV " + register + "," + direct(first));
                case 0xb -> new Instruction(3, "CJNE " + register + "," + immediate(first) + "," + relative(address, 3, second));
                case 0xc -> new Instruction(1, "XCH A," + register);
                case 0xd -> low < 8 ? new Instruction(1, "XCHD A," + register)
                        : new Instruction(2, "DJNZ " + register + "," + relative(address, 2, first));
                case 0xe -> new Instruction(1, "MOV A," + register);
                case 0xf -> new Instruction(1, "MOV " + register + ",A");
                default -> throw new AssertionError(opcode);
            };
        }
        String logic = switch (high) {
            case 0x4 -> "ORL";
            case 0x5 -> "ANL";
            case 0x6 -> "XRL";
            default -> "";
        };
        return switch (opcode) {
            case 0x00 -> new Instruction(1, "NOP");
            case 0x02 -> new Instruction(3, "LJMP " + word(first << 8 | second));
            case 0x03 -> new Instruction(1, "RR A");
            case 0x04 -> new Instruction(1, "INC A");
            case 0x05 -> new Instruction(2, "INC " + direct(first));
            case 0x10 -> new Instruction(3, "JBC " + bit(first) + "," + relative(address, 3, second));
            case 0x12 -> new Instruction(3, "LCALL " + word(first << 8 | second));
            case 0x13 -> new Instruction(1, "RRC A");
            case 0x14 -> new Instruction(1, "DEC A");
            case 0x15 -> new Instruction(2, "DEC " + direct(first));
            case 0x20 -> new Instruction(3, "JB " + bit(first) + "," + relative(address, 3, second));
            case 0x22 -> new Instruction(1, "RET");
            case 0x23 -> new Instruction(1, "RL A");
            case 0x24 -> new Instruction(2, "ADD A," + immediate(first));
            case 0x25 -> new Instruction(2, "ADD A," + direct(first));
            case 0x30 -> new Instruction(3, "JNB " + bit(first) + "," + relative(address, 3, second));
            case 0x32 -> new Instruction(1, "RETI");
            case 0x33 -> new Instruction(1, "RLC A");
            case 0x34 -> new Instruction(2, "ADDC A," + immediate(first));
            case 0x35 -> new Instruction(2, "ADDC A," + direct(first));
            case 0x40 -> new Instruction(2, "JC " + relative(address, 2, first));
            case 0x50 -> new Instruction(2, "JNC " + relative(address, 2, first));
            case 0x60 -> new Instruction(2, "JZ " + relative(address, 2, first));
            case 0x42, 0x52, 0x62 -> new Instruction(2, logic + " " + direct(first) + ",A");
            case 0x43, 0x53, 0x63 -> new Instruction(3, logic + " " + direct(first) + "," + immediate(second));
            case 0x44, 0x54, 0x64 -> new Instruction(2, logic + " A," + immediate(first));
            case 0x45, 0x55, 0x65 -> new Instruction(2, logic + " A," + direct(first));
            case 0x70 -> new Instruction(2, "JNZ " + relative(address, 2, first));
            case 0x72 -> new Instruction(2, "ORL C," + bit(first));
            case 0x73 -> new Instruction(1, "JMP @A+DPTR");
            case 0x74 -> new Instruction(2, "MOV A," + immediate(first));
            case 0x75 -> new Instruction(3, "MOV " + direct(first) + "," + immediate(second));
            case 0x80 -> new Instruction(2, "SJMP " + relative(address, 2, first));
            case 0x82 -> new Instruction(2, "ANL C," + bit(first));
            case 0x83 -> new Instruction(1, "MOVC A,@A+PC");
            case 0x84 -> new Instruction(1, "DIV AB");
            case 0x85 -> new Instruction(3, "MOV " + direct(second) + "," + direct(first));
            case 0x90 -> new Instruction(3, "MOV DPTR,#" + word(first << 8 | second));
            case 0x92 -> new Instruction(2, "MOV " + bit(first) + ",C");
            case 0x93 -> new Instruction(1, "MOVC A,@A+DPTR");
            case 0x94 -> new Instruction(2, "SUBB A," + immediate(first));
            case 0x95 -> new Instruction(2, "SUBB A," + direct(first));
            case 0xa0 -> new Instruction(2, "ORL C,/" + bit(first));
            case 0xa2 -> new Instruction(2, "MOV C," + bit(first));
            case 0xa3 -> new Instruction(1, "INC DPTR");
            case 0xa4 -> new Instruction(1, "MUL AB");
            case 0xa5 -> new Instruction(1, "DB 0A5h");
            case 0xb0 -> new Instruction(2, "ANL C,/" + bit(first));
            case 0xb2 -> new Instruction(2, "CPL " + bit(first));
            case 0xb3 -> new Instruction(1, "CPL C");
            case 0xb4 -> new Instruction(3, "CJNE A," + immediate(first) + "," + relative(address, 3, second));
            case 0xb5 -> new Instruction(3, "CJNE A," + direct(first) + "," + relative(address, 3, second));
            case 0xc0 -> new Instruction(2, "PUSH " + direct(first));
            case 0xc2 -> new Instruction(2, "CLR " + bit(first));
            case 0xc3 -> new Instruction(1, "CLR C");
            case 0xc4 -> new Instruction(1, "SWAP A");
            case 0xc5 -> new Instruction(2, "XCH A," + direct(first));
            case 0xd0 -> new Instruction(2, "POP " + direct(first));
            case 0xd2 -> new Instruction(2, "SETB " + bit(first));
            case 0xd3 -> new Instruction(1, "SETB C");
            case 0xd4 -> new Instruction(1, "DA A");
            case 0xd5 -> new Instruction(3, "DJNZ " + direct(first) + "," + relative(address, 3, second));
            case 0xe0 -> new Instruction(1, "MOVX A,@DPTR");
            case 0xe2, 0xe3 -> new Instruction(1, "MOVX A,@R" + (opcode & 1));
            case 0xe4 -> new Instruction(1, "CLR A");
            case 0xe5 -> new Instruction(2, "MOV A," + direct(first));
            case 0xf0 -> new Instruction(1, "MOVX @DPTR,A");
            case 0xf2, 0xf3 -> new Instruction(1, "MOVX @R" + (opcode & 1) + ",A");
            case 0xf4 -> new Instruction(1, "CPL A");
            case 0xf5 -> new Instruction(2, "MOV " + direct(first) + ",A");
            default -> throw new AssertionError(opcode);
        };
    }

    private static int operand(byte[] bytes, int offset) {
        return offset < bytes.length ? bytes[offset] & 0xff : 0xff;
    }

    private static String relative(int address, int length, int offset) {
        return word((address + length + (byte) offset) & 0xffff);
    }

    private static String word(int value) {
        return hex(String.format("%04X", value));
    }

    private static String hex(String digits) {
        return (digits.charAt(0) >= 'A' ? "0" : "") + digits + "h";
    }

    private static String immediate(int value) {
        return "#" + hex(String.format("%02X", value));
    }

    private static String bit(int value) {
        int address = value < 0x80 ? 0x20 + (value >>> 3) : value & 0xf8;
        return direct(address) + "." + (value & 7);
    }

    private static String direct(int address) {
        return switch (address) {
            case 0x80 -> "P0";
            case 0x81 -> "SP";
            case 0x82 -> "DPL";
            case 0x83 -> "DPH";
            case 0x87 -> "PCON";
            case 0x88 -> "TCON";
            case 0x89 -> "TMOD";
            case 0x8a -> "TL0";
            case 0x8b -> "TL1";
            case 0x8c -> "TH0";
            case 0x8d -> "TH1";
            case 0x90 -> "P1";
            case 0x98 -> "SCON";
            case 0x99 -> "SBUF";
            case 0xa0 -> "P2";
            case 0xa2 -> "AUXR1";
            case 0xa8 -> "IE";
            case 0xb0 -> "P3";
            case 0xb8 -> "IP";
            case 0xc8 -> "T2CON";
            case 0xca -> "RCAP2L";
            case 0xcb -> "RCAP2H";
            case 0xcc -> "TL2";
            case 0xcd -> "TH2";
            case 0xd0 -> "PSW";
            case 0xe0 -> "ACC";
            case 0xf0 -> "B";
            default -> hex(String.format("%02X", address));
        };
    }
}
