      ******************************************************************
      * PGMDRV - stand-in for "EXEC PGM=<program>,PARM='<parm>'".
      *
      *   pgmdrv <PROGRAM> [<PARM>]
      *
      * Builds the z/OS batch PARM block (halfword length + text, max
      * 100 bytes) and CALLs the program dynamically (PROGRAM.so on
      * COB_LIBRARY_PATH) USING it, so programs with
      * "PROCEDURE DIVISION USING <parm-block>" run unmodified.
      * Programs without USING ignore the argument. The program's
      * RETURN-CODE becomes the process exit status.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. PGMDRV.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  WS-ARGC                 PIC 9(04).
       01  WS-PROGRAM              PIC X(08).
       01  WS-PARM-TEXT            PIC X(100).
       01  WS-PARM-BLOCK.
           05  WS-PARM-LENGTH      PIC S9(04) COMP.
           05  WS-PARM-DATA        PIC X(100).
       PROCEDURE DIVISION.
           ACCEPT WS-ARGC FROM ARGUMENT-NUMBER
           IF WS-ARGC < 1
               DISPLAY 'PGMDRV: usage: pgmdrv <PROGRAM> [<PARM>]'
                   UPON SYSERR
               MOVE 16 TO RETURN-CODE
               STOP RUN
           END-IF
           DISPLAY 1 UPON ARGUMENT-NUMBER
           ACCEPT WS-PROGRAM FROM ARGUMENT-VALUE
           MOVE SPACES TO WS-PARM-TEXT WS-PARM-DATA
           MOVE 0 TO WS-PARM-LENGTH
           IF WS-ARGC > 1
               ACCEPT WS-PARM-TEXT FROM ARGUMENT-VALUE
               IF WS-PARM-TEXT NOT = SPACES
                   COMPUTE WS-PARM-LENGTH = FUNCTION LENGTH(
                       FUNCTION TRIM(WS-PARM-TEXT TRAILING))
                   MOVE WS-PARM-TEXT TO WS-PARM-DATA
               END-IF
           END-IF
           MOVE 0 TO RETURN-CODE
           CALL WS-PROGRAM USING WS-PARM-BLOCK
               ON EXCEPTION
                   DISPLAY 'PGMDRV: cannot load program ' WS-PROGRAM
                       UPON SYSERR
                   MOVE 16 TO RETURN-CODE
           END-CALL
           STOP RUN.
