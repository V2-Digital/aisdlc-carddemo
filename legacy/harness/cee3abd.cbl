      ******************************************************************
      * CEE3ABD - stub for the Language Environment abend service.
      *
      *   CALL 'CEE3ABD' USING abend-code, timing
      *
      * Prints the user abend code to stderr and ends the run unit with
      * exit status 16 (z/OS would end the step with ABEND Unnnn).
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. CEE3ABD.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  WS-CODE                 PIC 9(04).
       01  WS-TIMING               PIC -(9)9.
       LINKAGE SECTION.
       01  LS-ABCODE               PIC S9(9) BINARY.
       01  LS-TIMING               PIC S9(9) BINARY.
       PROCEDURE DIVISION USING LS-ABCODE LS-TIMING.
           MOVE LS-ABCODE TO WS-CODE
           MOVE LS-TIMING TO WS-TIMING
           DISPLAY 'CEE3ABD: ABEND U' WS-CODE ' TIMING='
               FUNCTION TRIM(WS-TIMING) UPON SYSERR
           MOVE 16 TO RETURN-CODE
           STOP RUN.
