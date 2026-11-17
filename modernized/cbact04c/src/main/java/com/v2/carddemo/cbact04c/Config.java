package com.v2.carddemo.cbact04c;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;

/** Run configuration: PARM, dataset paths by DD name, clock and SYSOUT. */
public record Config(String parm, Path tcatbalf, Path xreffile, Path discgrp, Path acctfile,
                     Path transact, Clock clock, PrintStream sysout, PrintStream syserr) {}
