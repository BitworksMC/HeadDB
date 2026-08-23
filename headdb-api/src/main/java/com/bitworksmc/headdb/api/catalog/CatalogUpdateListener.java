package com.bitworksmc.headdb.api.catalog;

@FunctionalInterface
public interface CatalogUpdateListener {
    void onCatalogUpdate(CatalogUpdate update);
}
