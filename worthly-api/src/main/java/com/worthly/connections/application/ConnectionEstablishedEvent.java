package com.worthly.connections.application;

import java.util.UUID;

public record ConnectionEstablishedEvent(UUID userId, UUID connectionId) {}
