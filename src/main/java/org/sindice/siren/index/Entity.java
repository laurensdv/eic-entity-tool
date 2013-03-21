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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An entity of the dataset
 */
public class Entity {

  /* incoming-triples.nt */
  ConcurrentHashMap<String, HashSet<String>> inTuples = new ConcurrentHashMap<String, HashSet<String>>();
  /* outgoing-triples.nt */
  ConcurrentHashMap<String, HashSet<String>> outTuples = new ConcurrentHashMap<String, HashSet<String>>();
  /* metadata */
  final StringBuilder sbMetadata = new StringBuilder();
  /* rdf:type statement's objects */
  HashSet<String> type = new HashSet<String>();
  HashSet<String> label = new HashSet<String>();
  HashSet<String> description = new HashSet<String>();
  
  final StringBuilder sb = new StringBuilder();
  
  String subject = ""; // The URI of the entity
  String context = ""; // The URL of the document where the entity is from
  
  public Entity() {
	  // do nothing
  }
  
  public Entity(Entity entity) {
	  this.inTuples = entity.inTuples;
	  this.outTuples = entity.outTuples;
	  this.type = entity.type;
	  this.label = entity.label;
	  this.description = entity.description;
	  this.subject = entity.subject;
	  this.context = entity.context;
  }
  
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
    final ConcurrentHashMap<String, HashSet<String>> map = out ? this.outTuples : this.inTuples;
    
    sb.setLength(0);
    for (Entry<String, HashSet<String>> e : Collections.synchronizedSet(map.entrySet())) {
    	for (String s : e.getValue()){
      sb.append(subject).append(' ').append(e.getKey()).append(' ').append(s).append(" .\n");
    	}
    }
    return sb.toString();
  }
  
}
