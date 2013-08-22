/**
 * Copyright 2011, Campinas Stephane
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/**
 * @project trec-entity-tool
 * @author Campinas Stephane [ 3 Jun 2011 ]
 * @link stephane.campinas@deri.org
 */
package org.sindice.siren.index;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * An entity of the dataset
 */
public class Entity {

  /* incoming-triples.nt */
  final ConcurrentHashMap<String, ConcurrentSkipListSet<String>> inTuples = new ConcurrentHashMap<String, ConcurrentSkipListSet<String>>();
  /* outgoing-triples.nt */
  final ConcurrentHashMap<String, ConcurrentSkipListSet<String>> outTuples = new ConcurrentHashMap<String, ConcurrentSkipListSet<String>>();
  /* metadata */
  final StringBuilder sbMetadata = new StringBuilder();
  /* rdf:type statement's objects */
  final ConcurrentSkipListSet<String> type = new ConcurrentSkipListSet<String>();
  final ConcurrentSkipListSet<String> label = new ConcurrentSkipListSet<String>();
  final ConcurrentSkipListSet<String> description = new ConcurrentSkipListSet<String>();
  
  final StringBuilder sb = new StringBuilder();
  
  String subject = ""; // The URI of the entity
  String context = ""; // The URL of the document where the entity is from

  public synchronized void clear() {
    subject = "";
    context = "";
    inTuples.clear();
    outTuples.clear();
    sb.setLength(0);
    type.clear();
    label.clear();
    description.clear();
    sbMetadata.setLength(0);
  }
  
  public synchronized String getTriples(boolean out) {
    final ConcurrentHashMap<String, ConcurrentSkipListSet<String>> map = out ? this.outTuples : this.inTuples;
    
    sb.setLength(0);
<<<<<<< HEAD
    for (Entry<String, HashSet<String>> e : map.entrySet()) {
    	for (String s : e.getValue()){
		if ( (subject.contains("<") && subject.contains(">")) || subject.indexOf('_') == 0)
			sb.append(subject).append(' ').append(e.getKey()).append(' ').append(s).append(" .\n");
		else
			sb.append('<').append(subject).append('>').append(' ').append(e.getKey()).append(' ').append(s).append(" .\n");
	}
=======
    for (Entry<String, ConcurrentSkipListSet<String>> e : Collections.synchronizedSet(map.entrySet())) {
    	for (String s : Collections.synchronizedSet(e.getValue())){
    		sb.append(subject).append(' ').append(e.getKey()).append(' ').append(s).append(" .\n");
    	}
>>>>>>> 10346c8e24331b43d4b615cfb9d0914d2a072a2e
    }
    return sb.toString();
  }
  
}
