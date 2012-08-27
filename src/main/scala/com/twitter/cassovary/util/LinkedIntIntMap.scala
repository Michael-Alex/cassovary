/*
 * Copyright 2012 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.twitter.cassovary.util

import com.google.common.annotations.VisibleForTesting

/**
 * An Int -> Int map with a backing doubly linked list
 * Especially useful in representing a cache
 * The linked list is implemented as a set of arrays and pointers
 * Any id added to the map must be > 0, as 0 indicates a null entry
 * If 0 is added, behavior is undefined
 * - O(1) get
 * - O(1) insert
 * - O(1) delete
 * The size of this map is O(3n+m),
 * where n is the size of the map, and m is maxId
 * @param maxId the maximum id of any element that will be inserted
 * @param size the size of this map
 */
class LinkedIntIntMap(maxId: Int, size: Int) {
  private val indexNext = new Array[Int](size + 1) // cache next pointers
  private val indexPrev = new Array[Int](size + 1) // cache prev pointers
  private var head, tail = 0 // pointers to the head and tail of the cache
  private var currentSize = 0 // current size of the cache
  private val indexToId = new Array[Int](size + 1) // cache index -> id
  private val idToIndex = new Array[Int](maxId + 1) // id -> cache index

  // Initialize a linked list of free indices using the free slots of indexNext
  (1 until size).foreach {
    i => indexNext(i) = i + 1
  }
  indexNext(size) = 0
  private var freePoint = 1 // pointer to first free slot

  /**
   * Add a free slot to the cache
   *
   * @param index index of free slot
   */
  private def addToFree(index: Int): Unit = {
    currentSize -= 1
    indexNext(index) = freePoint
    freePoint = index
  }

  /**
   * Get a free slot in the cache
   *
   * @return index of free slot
   */
  private def popFromFree(): Int = {
    currentSize += 1
    val popped = freePoint
    freePoint = indexNext(freePoint)
    popped
  }

  /**
   * Remove the tail element of the list and return it
   *
   * @return id of tail
   */
  def removeFromTail(): Int = {
    if (currentSize == 0) throw new IllegalArgumentException("Nothing left in the cache to remove!")

    val prevTail = tail
    val prevId = indexToId(prevTail)
    tail = indexNext(prevTail)
    addToFree(prevTail)
    idToIndex(prevId) = 0
    // indexToId(prevTail) = 0 // probably don't need this
    indexPrev(tail) = 0
    prevId
  }

  /**
   * Move an element to the front of the linked list
   * Cases - moving an element in between the head and tail, only 1 element,
   * moving the tail itself, moving the head itself
   *
   * @param id id of element to move
   */
  def moveToHead(id: Int) {
    val idx = idToIndex(id)
    moveIndexToHead(idx)
  }

  /**
   * Move an element at the given cache index to the front of the linked list
   *
   * @param idx index of element to move
   */
  def moveIndexToHead(idx: Int) {
    if (idx == 0) throw new IllegalArgumentException("Id doesn't exist in cache!")

    if (idx != head) {
      // Implicitly means currIndexCapacity > 1
      val prevIdx = indexPrev(idx)
      val nextIdx = indexNext(idx)
      val prevHeadIdx = head

      // Point to the real tail if we moved the tail
      // can add in && currentIndexCapacity > 1 if there's no idx != head check
      if (tail == idx) tail = nextIdx

      // Update pointers
      indexNext(prevIdx) = nextIdx
      indexPrev(nextIdx) = prevIdx
      indexNext(idx) = 0
      indexPrev(idx) = prevHeadIdx
      indexNext(prevHeadIdx) = idx
      head = idx
    }
  }

  /**
   * Add an element to the head
   * Behavior is undefined if the same id is added several times to the head
   * Will throw an error if the cache is full
   * Cases - adding to 0 element, 1 element, >1 element list
   *
   * @param id
   */
  def addToHead(id: Int) {
    // TODO Consider checking if the id already exists
    if (currentSize == size) throw new IllegalArgumentException("Cache has no space!")

    val prevHeadIdx = head
    head = popFromFree()
    idToIndex(id) = head
    indexNext(prevHeadIdx) = head
    indexPrev(head) = prevHeadIdx
    indexToId(head) = id
    indexNext(head) = 0

    if (currentSize == 1) tail = head // Since tail gets set to 0 when last elt removed
  }

  /**
   * Get cache index of element at the tail
   *
   * @return index of tail element
   */
  def getTailIndex: Int = tail

  /**
   * Get cache index of element at the head
   *
   * @return index of head element
   */
  def getHeadIndex: Int = head

  /**
   * Check if id exists in the map
   *
   * @param id element to check
   * @return true if element exists in map
   */
  def contains(id: Int): Boolean = idToIndex(id) > 0

  /**
   * Get the array index of the given id, and id must exist
   *
   * @param id desired element
   * @return index of desired element
   */
  def getIndexFromId(id: Int): Int = idToIndex(id)

  /**
   * Get the id at the head (most recently accessed)
   */
  def getHeadId: Int = indexToId(head)

  /**
   * Get the id at the tail (next to be evicted)
   */
  def getTailId: Int = indexToId(tail)

  /**
   * Get the number of elements in the map
   */
  def getCurrentSize: Int = currentSize

  @VisibleForTesting
  def debug = {
    println("size, head, tail", currentSize, head, tail)
    println("next", indexNext.deep.mkString(" "))
    println("prev", indexPrev.deep.mkString(" "))
    println("indexToId", indexToId.deep.mkString(" "))
    println("idToIndex", idToIndex.deep.mkString(" "))
  }

}
