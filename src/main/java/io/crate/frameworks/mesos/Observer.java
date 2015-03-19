package io.crate.frameworks.mesos;

public interface Observer<ObservedType> {

    public void update(ObservedType data);
}
