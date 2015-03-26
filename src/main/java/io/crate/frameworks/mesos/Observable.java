package io.crate.frameworks.mesos;

import java.io.IOException;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;


public class Observable<ObservedType extends Serializable> implements Serializable {

    private List<Observer<ObservedType>> observers = new LinkedList<>();
    private ObservedType value;

    public Observable(ObservedType value) {
        this.value = value;
    }

    public ObservedType getValue(){
        return this.value;
    }

    public void setValue(ObservedType value){
        this.value = value;
        this.notifyObservers(this.value);
    }

    public void addObserver(Observer<ObservedType> observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void notifyObservers(ObservedType value) {
        for (Observer<ObservedType> observer : observers) {
            observer.update(value);
        }
    }

    public void clearObservers() {
        observers.clear();
    }

    private void writeObject(java.io.ObjectOutputStream stream)
            throws IOException {
        stream.writeObject(value);
    }

    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        value = (ObservedType) stream.readObject();
        observers = new LinkedList<>();
    }
}
