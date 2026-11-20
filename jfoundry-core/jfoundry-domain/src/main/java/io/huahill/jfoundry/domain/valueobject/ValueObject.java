package io.huahill.jfoundry.domain.valueobject;

/// Marker interface for domain-layer value objects.
/// <p>
/// Business value objects implementing this interface receive:
/// <ul>
///   <li>jMolecules {@code ValueObject} type semantics recognized by jMolecules tooling</li>
///   <li>framework ArchUnit rule protection for immutability and equals/hashCode</li>
/// </ul>
/// <p>
/// Java 21 {@code record} types are recommended because records naturally
/// satisfy immutability and equals/hashCode contracts. Class implementations
/// must:
/// <ul>
///   <li>be declared {@code final}</li>
///   <li>declare all fields as {@code final}</li>
///   <li>override {@code equals} and {@code hashCode}</li>
/// </ul>
/// <p>
/// This interface adds no methods. It is a pure marker interface used for
/// business-visible typing after type erasure.
public interface ValueObject extends org.jmolecules.ddd.types.ValueObject {
}
