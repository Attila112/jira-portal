package hu.bplusn.portal.storage;

import java.util.List;

/**
 * Egyszerű dokumentumtár a portál kevés saját adatához (szabadságok, mentett személyek).
 * Egy dokumentum = (gyűjtemény, tulajdonos) párhoz tartozó szövegsorok.
 * Megvalósítások: {@link FileStorage} (helyi fájlok) és {@link JdbcStorage} (PostgreSQL, pl. Neon).
 */
public interface PortalStorage {

    List<String> read(String collection, String owner);

    void write(String collection, String owner, List<String> lines);

    String description();
}
