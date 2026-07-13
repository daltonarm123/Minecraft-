package com.community.servercore.storage;

import com.community.servercore.portal.Portal;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortalRepository {
    List<Portal> findAll();

    Optional<Portal> findById(UUID id);

    Optional<Portal> findByName(String name);

    void save(Portal portal) throws IOException;

    boolean delete(UUID id) throws IOException;
}
