package com.borjaglez.cqrs.fixtures.scan.command;

import com.borjaglez.cqrs.command.annotation.CommandHandler;

/** Abstract, so the handler scan must not register it. */
@CommandHandler
public abstract class AbstractScannedCommandHandler {}
