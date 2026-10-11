      ******************************************************************
      * CDFILES - CardDemo dataset loader / unloader for GnuCOBOL.
      *
      *   cdfiles LOAD   <DATASET>   text (TEXTFILE) -> dataset
      *   cdfiles UNLOAD <DATASET>   dataset -> text (TEXTFILE)
      *
      * Text form: one fixed-width record per line, LF line endings,
      * zoned decimal with EBCDIC-style overpunch signs ('{' 'A'-'I'
      * '}' 'J'-'R'), i.e. the same layout as app/data/ASCII/*.txt.
      * Run UNLOAD with COB_LS_FIXED=TRUE so trailing spaces are kept.
      *
      * DATASET is the JCL DD name; the physical file comes from the
      * DD_<name> environment variable (GnuCOBOL filename mapping).
      * Key definitions mirror the IDCAMS DEFINE CLUSTER/AIX in app/jcl:
      *   ACCTFILE  KSDS 300  KEYS(11 0)
      *   XREFFILE  KSDS  50  KEYS(16 0), AIX KEYS(11 25)
      *   TCATBALF  KSDS  50  KEYS(17 0)
      *   DISCGRP   KSDS  50  KEYS(16 0)
      *   TRANFILE  KSDS 350  KEYS(16 0)
      *   SEQ350    sequential RECFM=F LRECL=350 (DALYTRAN, TRANSACT)
      *   SEQ430    sequential RECFM=F LRECL=430 (DALYREJS)
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID. CDFILES.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT TEXT-FILE ASSIGN TO TEXTFILE
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-TXT-STATUS.
           SELECT ACCT-FILE ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS DYNAMIC
                  RECORD KEY   IS ACCT-KEY
                  FILE STATUS  IS WS-DAT-STATUS.
           SELECT XREF-FILE ASSIGN TO XREFFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS DYNAMIC
                  RECORD KEY   IS XREF-KEY
                  ALTERNATE RECORD KEY IS XREF-ACCT-KEY
                  FILE STATUS  IS WS-DAT-STATUS.
           SELECT TCATBAL-FILE ASSIGN TO TCATBALF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS DYNAMIC
                  RECORD KEY   IS TCATBAL-KEY
                  FILE STATUS  IS WS-DAT-STATUS.
           SELECT DISCGRP-FILE ASSIGN TO DISCGRP
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS DYNAMIC
                  RECORD KEY   IS DISCGRP-KEY
                  FILE STATUS  IS WS-DAT-STATUS.
           SELECT TRAN-FILE ASSIGN TO TRANFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS DYNAMIC
                  RECORD KEY   IS TRAN-KEY
                  FILE STATUS  IS WS-DAT-STATUS.
           SELECT SEQ350-FILE ASSIGN TO SEQ350
                  ORGANIZATION IS SEQUENTIAL
                  FILE STATUS  IS WS-DAT-STATUS.
           SELECT SEQ430-FILE ASSIGN TO SEQ430
                  ORGANIZATION IS SEQUENTIAL
                  FILE STATUS  IS WS-DAT-STATUS.
       DATA DIVISION.
       FILE SECTION.
       FD  TEXT-FILE
           RECORD IS VARYING IN SIZE FROM 1 TO 430 CHARACTERS
           DEPENDING ON WS-TXT-LEN.
       01  TEXT-REC                 PIC X(430).
       FD  ACCT-FILE.
       01  ACCT-REC.
           05 ACCT-KEY              PIC X(11).
           05 FILLER                PIC X(289).
       FD  XREF-FILE.
       01  XREF-REC.
           05 XREF-KEY              PIC X(16).
           05 FILLER                PIC X(09).
           05 XREF-ACCT-KEY         PIC X(11).
           05 FILLER                PIC X(14).
       FD  TCATBAL-FILE.
       01  TCATBAL-REC.
           05 TCATBAL-KEY           PIC X(17).
           05 FILLER                PIC X(33).
       FD  DISCGRP-FILE.
       01  DISCGRP-REC.
           05 DISCGRP-KEY           PIC X(16).
           05 FILLER                PIC X(34).
       FD  TRAN-FILE.
       01  TRAN-REC.
           05 TRAN-KEY              PIC X(16).
           05 FILLER                PIC X(334).
       FD  SEQ350-FILE.
       01  SEQ350-REC               PIC X(350).
       FD  SEQ430-FILE.
       01  SEQ430-REC               PIC X(430).
       WORKING-STORAGE SECTION.
       01  WS-MODE                  PIC X(08).
       01  WS-DATASET               PIC X(08).
       01  WS-TXT-STATUS            PIC X(02).
       01  WS-DAT-STATUS            PIC X(02).
       01  WS-TXT-LEN               PIC 9(04) COMP.
       01  WS-LRECL                 PIC 9(04) COMP.
       01  WS-COUNT                 PIC 9(09) VALUE 0.
       01  WS-EOF                   PIC X     VALUE 'N'.
       01  WS-DATA                  PIC X(430).
       PROCEDURE DIVISION.
       MAIN.
           DISPLAY 1 UPON ARGUMENT-NUMBER
           ACCEPT WS-MODE FROM ARGUMENT-VALUE
           ACCEPT WS-DATASET FROM ARGUMENT-VALUE
           EVALUATE WS-DATASET
               WHEN 'ACCTFILE' MOVE 300 TO WS-LRECL
               WHEN 'XREFFILE' MOVE  50 TO WS-LRECL
               WHEN 'TCATBALF' MOVE  50 TO WS-LRECL
               WHEN 'DISCGRP'  MOVE  50 TO WS-LRECL
               WHEN 'TRANFILE' MOVE 350 TO WS-LRECL
               WHEN 'SEQ350'   MOVE 350 TO WS-LRECL
               WHEN 'SEQ430'   MOVE 430 TO WS-LRECL
               WHEN OTHER
                   DISPLAY 'CDFILES: unknown dataset ' WS-DATASET
                       UPON SYSERR
                   PERFORM FAIL
           END-EVALUATE
           EVALUATE WS-MODE
               WHEN 'LOAD'   PERFORM DO-LOAD
               WHEN 'UNLOAD' PERFORM DO-UNLOAD
               WHEN OTHER
                   DISPLAY 'CDFILES: usage: cdfiles LOAD|UNLOAD <DD>'
                       UPON SYSERR
                   PERFORM FAIL
           END-EVALUATE
           DISPLAY 'CDFILES: ' WS-MODE ' ' WS-DATASET ' records='
               WS-COUNT UPON SYSERR
           STOP RUN.

       DO-LOAD.
           OPEN INPUT TEXT-FILE
           IF WS-TXT-STATUS NOT = '00'
               DISPLAY 'CDFILES: open text failed ' WS-TXT-STATUS
                   UPON SYSERR
               PERFORM FAIL
           END-IF
           PERFORM OPEN-DATA-OUTPUT
           PERFORM UNTIL WS-EOF = 'Y'
               MOVE SPACES TO TEXT-REC
               READ TEXT-FILE
                   AT END MOVE 'Y' TO WS-EOF
               END-READ
               IF WS-EOF = 'N'
                   IF WS-TXT-STATUS NOT = '00'
                       DISPLAY 'CDFILES: read text failed '
                           WS-TXT-STATUS ' after record ' WS-COUNT
                           UPON SYSERR
                       PERFORM FAIL
                   END-IF
                   IF WS-TXT-LEN > WS-LRECL
                       DISPLAY 'CDFILES: line longer than LRECL at '
                           'record ' WS-COUNT UPON SYSERR
                       PERFORM FAIL
                   END-IF
                   MOVE SPACES TO WS-DATA
                   MOVE TEXT-REC(1:WS-TXT-LEN) TO WS-DATA
                   PERFORM WRITE-DATA
                   ADD 1 TO WS-COUNT
               END-IF
           END-PERFORM
           CLOSE TEXT-FILE
           PERFORM CLOSE-DATA.

       DO-UNLOAD.
           PERFORM OPEN-DATA-INPUT
           OPEN OUTPUT TEXT-FILE
           IF WS-TXT-STATUS NOT = '00'
               DISPLAY 'CDFILES: open text failed ' WS-TXT-STATUS
                   UPON SYSERR
               PERFORM FAIL
           END-IF
           MOVE WS-LRECL TO WS-TXT-LEN
           PERFORM UNTIL WS-EOF = 'Y'
               PERFORM READ-DATA
               IF WS-EOF = 'N'
                   MOVE WS-DATA(1:WS-LRECL) TO TEXT-REC
                   WRITE TEXT-REC
                   IF WS-TXT-STATUS NOT = '00'
                       DISPLAY 'CDFILES: write text failed '
                           WS-TXT-STATUS UPON SYSERR
                       PERFORM FAIL
                   END-IF
                   ADD 1 TO WS-COUNT
               END-IF
           END-PERFORM
           CLOSE TEXT-FILE
           PERFORM CLOSE-DATA.

       OPEN-DATA-OUTPUT.
           EVALUATE WS-DATASET
               WHEN 'ACCTFILE' OPEN OUTPUT ACCT-FILE
               WHEN 'XREFFILE' OPEN OUTPUT XREF-FILE
               WHEN 'TCATBALF' OPEN OUTPUT TCATBAL-FILE
               WHEN 'DISCGRP'  OPEN OUTPUT DISCGRP-FILE
               WHEN 'TRANFILE' OPEN OUTPUT TRAN-FILE
               WHEN 'SEQ350'   OPEN OUTPUT SEQ350-FILE
               WHEN 'SEQ430'   OPEN OUTPUT SEQ430-FILE
           END-EVALUATE
           PERFORM CHECK-DAT-STATUS.

       OPEN-DATA-INPUT.
           EVALUATE WS-DATASET
               WHEN 'ACCTFILE' OPEN INPUT ACCT-FILE
               WHEN 'XREFFILE' OPEN INPUT XREF-FILE
               WHEN 'TCATBALF' OPEN INPUT TCATBAL-FILE
               WHEN 'DISCGRP'  OPEN INPUT DISCGRP-FILE
               WHEN 'TRANFILE' OPEN INPUT TRAN-FILE
               WHEN 'SEQ350'   OPEN INPUT SEQ350-FILE
               WHEN 'SEQ430'   OPEN INPUT SEQ430-FILE
           END-EVALUATE
           PERFORM CHECK-DAT-STATUS.

       WRITE-DATA.
           EVALUATE WS-DATASET
               WHEN 'ACCTFILE' WRITE ACCT-REC    FROM WS-DATA
               WHEN 'XREFFILE' WRITE XREF-REC    FROM WS-DATA
               WHEN 'TCATBALF' WRITE TCATBAL-REC FROM WS-DATA
               WHEN 'DISCGRP'  WRITE DISCGRP-REC FROM WS-DATA
               WHEN 'TRANFILE' WRITE TRAN-REC    FROM WS-DATA
               WHEN 'SEQ350'   WRITE SEQ350-REC  FROM WS-DATA
               WHEN 'SEQ430'   WRITE SEQ430-REC  FROM WS-DATA
           END-EVALUATE
           PERFORM CHECK-DAT-STATUS.

      *    Indexed files are read in primary-key order.
       READ-DATA.
           MOVE SPACES TO WS-DATA
           EVALUATE WS-DATASET
               WHEN 'ACCTFILE' READ ACCT-FILE NEXT INTO WS-DATA
                                   AT END MOVE 'Y' TO WS-EOF END-READ
               WHEN 'XREFFILE' READ XREF-FILE NEXT INTO WS-DATA
                                   AT END MOVE 'Y' TO WS-EOF END-READ
               WHEN 'TCATBALF' READ TCATBAL-FILE NEXT INTO WS-DATA
                                   AT END MOVE 'Y' TO WS-EOF END-READ
               WHEN 'DISCGRP'  READ DISCGRP-FILE NEXT INTO WS-DATA
                                   AT END MOVE 'Y' TO WS-EOF END-READ
               WHEN 'TRANFILE' READ TRAN-FILE NEXT INTO WS-DATA
                                   AT END MOVE 'Y' TO WS-EOF END-READ
               WHEN 'SEQ350'   READ SEQ350-FILE INTO WS-DATA
                                   AT END MOVE 'Y' TO WS-EOF END-READ
               WHEN 'SEQ430'   READ SEQ430-FILE INTO WS-DATA
                                   AT END MOVE 'Y' TO WS-EOF END-READ
           END-EVALUATE
           IF WS-EOF = 'N'
               PERFORM CHECK-DAT-STATUS
           END-IF.

       CLOSE-DATA.
           EVALUATE WS-DATASET
               WHEN 'ACCTFILE' CLOSE ACCT-FILE
               WHEN 'XREFFILE' CLOSE XREF-FILE
               WHEN 'TCATBALF' CLOSE TCATBAL-FILE
               WHEN 'DISCGRP'  CLOSE DISCGRP-FILE
               WHEN 'TRANFILE' CLOSE TRAN-FILE
               WHEN 'SEQ350'   CLOSE SEQ350-FILE
               WHEN 'SEQ430'   CLOSE SEQ430-FILE
           END-EVALUATE
           PERFORM CHECK-DAT-STATUS.

       CHECK-DAT-STATUS.
           IF WS-DAT-STATUS NOT = '00'
               DISPLAY 'CDFILES: ' WS-MODE ' ' WS-DATASET
                   ' file status ' WS-DAT-STATUS
                   ' at record ' WS-COUNT UPON SYSERR
               PERFORM FAIL
           END-IF.

       FAIL.
           MOVE 12 TO RETURN-CODE
           STOP RUN.
