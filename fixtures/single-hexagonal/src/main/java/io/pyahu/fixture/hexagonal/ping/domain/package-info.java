/**
 * The ping domain. Deliberately not annotated with jMolecules {@code @Application}: under
 * {@code ensureHexagonal(STRICT)} ports may not reference the Application layer, and ports
 * here use domain types. ArchitectureTest constrains this package instead.
 */
@NullMarked
package io.pyahu.fixture.hexagonal.ping.domain;

import org.jspecify.annotations.NullMarked;
