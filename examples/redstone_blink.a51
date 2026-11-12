; Hand-assembled source for redstone_blink.hex, Intel 8051 syntax.
; 11.0592 MHz / 12T, 20 ticks per second. Counter is internal RAM 20h.
ORG 0000h
    LJMP main
ORG 000Bh
    LJMP timer0_isr
ORG 0020h
timer0_isr:
    CLR TR0
    MOV TH0, #04Ch
    MOV TL0, #00h
    SETB TR0
    INC 020h
    MOV A, 020h
    CJNE A, #10, done
    MOV 020h, #00h
    CPL P1.2
done:
    RETI
ORG 0050h
main:
    MOV P1, #00h
    MOV TMOD, #01h
    MOV TH0, #04Ch
    MOV TL0, #00h
    MOV IE, #082h
    SETB TR0
forever:
    SJMP forever
END