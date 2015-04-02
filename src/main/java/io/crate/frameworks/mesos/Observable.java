/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

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
