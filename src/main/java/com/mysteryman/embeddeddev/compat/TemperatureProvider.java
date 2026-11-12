package com.mysteryman.embeddeddev.compat;

/** Celsius reading supplied by a block entity or the embeddeddev:temperature block capability. */
@FunctionalInterface
public interface TemperatureProvider { double celsius(); }
