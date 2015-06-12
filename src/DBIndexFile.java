/**
Implementation of B+ tree indexing. This program will read a text file
containing data and builds an index , treating the first 'n' bytes as the key.
Apart from creating an index, the program will help you search a record by the
key, insert a new text record and list sequential records with a key as reference.

Course: CS6360 - Database Design
Authors: Prabhmanmeet Singh 
		 Yogeshwara Krishnan
Last Modified: 11/26/2013
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// An instance of BTreeNode represents a node in the B+ Tree.
class BTreeNode implements Serializable {
	public List<String> key; // Stores vector of keys.
	public List<BTreeNode> ptr; // Vector of pointers to the children nodes if
								// any.
	public List<Long> byteOffset; // Byte offset of the data in data file. In
									// other
	// words this is the data pointer.
	public List<Integer> dataLength; // Length of the data in bytes for each
										// key.
	public BTreeNode immediateParent; // Pointer to the immediate parent.
	public BTreeNode nextLeafPointer; // Applicable to only leaf nodes.
	public BTreeNode prevLeafPointer; // Applicable to only leaf nodes.
	public boolean isLeaf; // Checks if the node is a leaf node or intermediate
							// node. If set to true, then the corresponding node
							// is true.

	// Constructor.
	public BTreeNode() {
		this.key = new ArrayList<String>();
		this.ptr = new ArrayList<BTreeNode>();
		this.byteOffset = new ArrayList<Long>();
		this.dataLength = new ArrayList<Integer>();
		this.immediateParent = null;
		this.nextLeafPointer = null;
		this.prevLeafPointer = null;
		this.isLeaf = false;
	}
}

// Main class
public class DBIndexFile {

	static BTreeNode root; // This acts as our root node.
	static int DEGREE = 0; // Degree of a node determines the no. of keys to be
							// stored in a node.

	/**
	 * A recursive function which inserts key and data pointer into the B+ tree.
	 * This function checks for duplicates and doesn't allow them to be
	 * inserted. The B+ Tree is traversed till the leaf node and the key
	 * inserted maintaining the sort order. If the degree exceeds, the tree is
	 * balanced.
	 * 
	 * @param pNode
	 *            is the candidate node where the key might be inserted.
	 * @param pKeyValue
	 *            is the key value to be inserted.
	 * @param pByteOffset
	 *            is the byte offset of the key in the input file.
	 * @param pDataLength
	 *            length of the data.
	 * @author Yogeshwara Krishnan
	 */
	static void insertIntoBTree(BTreeNode pNode, String pKeyValue,
			long pByteOffset, int pDataLength) {
		/* Added by Yogesh on 11-25-2013 */
		// Check if the B+ Tree is empty.
		// If empty create a new node and insert the key and byte offset.
		if ((pNode == null || pNode.key.isEmpty()) && pNode == root) {
			pNode.key.add(pKeyValue);
			pNode.byteOffset.add((Long) pByteOffset);
			pNode.dataLength.add(pDataLength);
			pNode.isLeaf = true;
			root = pNode;
			// Write root to file
			return;
		}

		// If the parameter p_node is not empty then traverse through the tree
		// till leaf
		// and insert the key and the byte offset.
		else if (pNode != null || !pNode.key.isEmpty()) {
			for (int count = 0; count < pNode.key.size(); count++) {
				// Check if the key already exists. If it exists, don't allow
				// insertion.
				if (pKeyValue.compareTo(pNode.key.get(count)) == 0) {
					System.out.println("Data exists. Duplicates not allowed.");
					return;
				}

				// Else traverse through the node to find appropriate place to
				// insert the key.
				// pKeyValue is Less than current key[count]
				else if (pKeyValue.compareTo(pNode.key.get(count)) < 0) {
					if (!pNode.isLeaf && pNode.ptr.get(count) != null) {
						// Write pNode to file
						insertIntoBTree((BTreeNode) pNode.ptr.get(count),
								pKeyValue, pByteOffset, pDataLength);
						return;
					} else if (pNode.isLeaf) {
						// Push dummy values at the end of the key and byte
						// offset arrays to avoid
						// out of range exceptions.
						pNode.key.add("");
						pNode.byteOffset.add(0l);
						pNode.dataLength.add(0);

						for (int j = pNode.key.size() - 2; j >= count; j--) {
							pNode.key.set(j + 1, pNode.key.get(j));
							pNode.byteOffset
									.set(j + 1, pNode.byteOffset.get(j));
							pNode.dataLength
									.set(j + 1, pNode.dataLength.get(j));
						}

						pNode.key.set(count, pKeyValue);
						pNode.byteOffset.set(count, pByteOffset);
						pNode.dataLength.set(count, pDataLength);

						// Check the degree of the node. If the degree exceeds
						// balance the node and then write to file.

						if (pNode.key.size() == DEGREE) {
							balance(pNode);
							return;
						} else {
							return;
						}

					}
				}

				// pKeyValue is Greater than current key[count]
				else if (pKeyValue.compareTo(pNode.key.get(count)) > 0) {
					if (count < pNode.key.size() - 1) {
						continue;
					}

					// End of the right child.
					else if (count == pNode.key.size() - 1) {
						if (!pNode.isLeaf && pNode.ptr.get(count + 1) != null) {
							// Write pNode to file, before traversing to right subTree.
							insertIntoBTree(
									(BTreeNode) pNode.ptr.get(count + 1),
									pKeyValue, pByteOffset, pDataLength);
							return;
						}

						else if (pNode.isLeaf) {
							pNode.key.add("");
							pNode.byteOffset.add(0l);
							pNode.dataLength.add(0);
							pNode.key.set(count + 1, pKeyValue);
							pNode.byteOffset.set(count + 1, pByteOffset);
							pNode.dataLength.set(count + 1, pDataLength);
						}

						// Check the degree of node and balance the node.
						if (pNode.key.size() == DEGREE) {
							balance(pNode);
							return;
						} else
							return;
					}
				}
			}
		}
	}

	/**
	 * A recursive function which balances the unstable nodes; leaf or
	 * intermediate; in the B+ tree.
	 * 
	 * @param pNode
	 *            is the node to be balanced.
	 * @author Prabhmanmeet Singh Last Modified: Prabhmanmeet Singh On:
	 *         11-28-2013
	 */
	static void balance(BTreeNode pNode) {
		BTreeNode bLeft = new BTreeNode(); // New left child
		BTreeNode bRight = new BTreeNode(); // New right child
		BTreeNode bPrime = new BTreeNode(); // New parent / root.
		BTreeNode parent;

		int newPosKey = 0, split = 0, temp = 0;

		// Check if the node to be balanced is a leaf node.
		if (pNode.isLeaf) {
			// bRight is leaf node
			bRight.isLeaf = true;
			// Calculate where to split the pNode.
			if (pNode.key.size() % 2 == 0)
				split = (pNode.key.size() / 2) - 1;
			else
				split = pNode.key.size() / 2;

			// Populate the right child.
			for (int count = split; count < pNode.key.size(); count++) {
				bRight.key.add(pNode.key.get(count));
				bRight.byteOffset.add(pNode.byteOffset.get(count));
				bRight.dataLength.add(pNode.dataLength.get(count));
			}

			// Populate the left child.
			bLeft.isLeaf = true;
			for (int count = 0; count < split; count++) {
				bLeft.key.add(pNode.key.get(count));
				bLeft.byteOffset.add(pNode.byteOffset.get(count));
				bLeft.dataLength.add(pNode.dataLength.get(count));
			}

			// Reassign the next pointers
			if (pNode.nextLeafPointer != null)
				bRight.nextLeafPointer = pNode.nextLeafPointer;
			else
				bRight.nextLeafPointer = null;

			// Reassign the previous pointers
			if (pNode.prevLeafPointer != null)
				bLeft.prevLeafPointer = pNode.prevLeafPointer;
			else
				bLeft.prevLeafPointer = null;

			// Point bLeft's next pointer to bRight.
			bLeft.nextLeafPointer = bRight;
			// Point bRight's previous pointer to bLeft.
			bRight.prevLeafPointer = bLeft;

			// If Parent is not present.
			if (pNode.immediateParent == null) {
				bPrime.isLeaf = false;
				bPrime.key.add(bRight.key.get(0));
				bPrime.ptr.add(bLeft);
				bPrime.ptr.add(bRight);
				bLeft.immediateParent = bPrime;
				bRight.immediateParent = bPrime;
				root = bPrime;
				pNode = bPrime;
				// Write bLeft to file, left SubTree saved to file.
				// Write bRight to file, right SubTree saved to file.
				// Write parent node to file.
			}
			// If Parent is present.
			else if (pNode.immediateParent != null) {
				// Get the parent of pNode.
				parent = pNode.immediateParent;
				// Write pNode to file, after loading parent.
				
				parent.key.add(bRight.key.get(0));
				// Keys are Balanced
				Collections.sort(parent.key);
				bLeft.immediateParent = parent;
				bRight.immediateParent = parent;
				newPosKey = parent.key.indexOf(bRight.key.get(0));

				// Balancing the Pointers
				// Check if the key has been inserted at the end or somewhere in
				// the middle.
				if (newPosKey < parent.key.size() - 1) {
					parent.ptr.add(null);

					// Right shift the pointer
					for (int count = parent.key.size() - 1; count > newPosKey; count--) {
						parent.ptr.set(count + 1, parent.ptr.get(count));
					}

					// Make the parent point to the new left and right children.
					parent.ptr.set(newPosKey + 1, bRight);
					parent.ptr.set(newPosKey, bLeft);
				}

				else if (newPosKey == parent.key.size() - 1) {
					// Setting bLeft as next pointer.
					parent.ptr.set(newPosKey, bLeft);
					parent.ptr.add(bRight);
				}

				// Rearrange the next and previous pointers
				if (pNode.prevLeafPointer != null) {
					pNode.prevLeafPointer.nextLeafPointer = bLeft;
					bLeft.prevLeafPointer = pNode.prevLeafPointer;
				}
				// Setting bRight as next pointer.
				if (pNode.nextLeafPointer != null) {
					pNode.nextLeafPointer.prevLeafPointer = bRight;
					bRight.nextLeafPointer = pNode.nextLeafPointer;
				}

				// Check if the parent needs to be balanced.
				if (parent.key.size() == DEGREE) {
					// Write bLeft and bRight to file, before traversing to Parent Node.
					balance(parent);
					return;
				} else
					return;
			}
		}

		// If the node to be balanced is not a leaf node
		else if (!pNode.isLeaf) {
			bRight.isLeaf = false;
			// Calculate where exactly to split.
			if (pNode.key.size() % 2 == 0)
				split = (pNode.key.size() / 2) - 1;
			else
				split = pNode.key.size() / 2;

			String popKey = pNode.key.get(split);
			int k = 0, p = 0;

			// Populate the right non leaf node.
			for (int count = split + 1; count < pNode.key.size(); count++) {
				bRight.key.add(pNode.key.get(count));
			}

			// Copy the node pointers too to the right child.
			for (int count = split + 1; count < pNode.ptr.size(); count++) {
				bRight.ptr.add(pNode.ptr.get(count));
				bRight.ptr.get(k++).immediateParent = bRight;
			}
			k = 0;
			// Populate the left child with key and node pointers.
			for (int count = 0; count < split; count++) {
				bLeft.key.add(pNode.key.get(count));
			}

			// Copy the node pointers too to the left child.
			for (int count = 0; count < split + 1; count++) {
				bLeft.ptr.add(pNode.ptr.get(count));
				bLeft.ptr.get(p++).immediateParent = bLeft;
			}

			p = 0;

			// If the intermediate node has no parent.
			if (pNode.immediateParent == null) {
				// Create a new parent. Note that this becomes the root.
				bPrime.isLeaf = false;
				bPrime.key.add(popKey);
				bPrime.ptr.add(bLeft);
				bPrime.ptr.add(bRight);
				bLeft.immediateParent = bPrime;
				bRight.immediateParent = bPrime;
				pNode = bPrime;
				root = bPrime;
				// Write bLeft to file, save the Left SubTree to file.
				// Write bRight to file, save the Right SubTree to file.
				// Write parent to file.
				return;
			}

			// If intermediate node has parent.
			else if (pNode.immediateParent != null) {
				parent = pNode.immediateParent;
				parent.key.add(popKey);
				// Balance the keys in parent.
				Collections.sort(parent.key);
				newPosKey = parent.key.indexOf(popKey);

				// If key added is at the end
				if (newPosKey == parent.key.size() - 1) {
					parent.ptr.set(newPosKey, bLeft);
					parent.ptr.add(bRight);
					bRight.immediateParent = parent;
					bLeft.immediateParent = parent;
				}

				else if (newPosKey < parent.key.size() - 1) {
					int ptrSize = parent.ptr.size();
					parent.ptr.add(null);

					// Balancing the pointers in parent.
					for (int count = ptrSize - 1; count > newPosKey; count--) {
						parent.ptr.set(count + 1, parent.ptr.get(count));
					}

					parent.ptr.set(newPosKey, bLeft);
					parent.ptr.set(newPosKey + 1, bRight);

					bLeft.immediateParent = parent;
					bRight.immediateParent = parent;
				}

				// Balance the parent if needed.
				if (parent.key.size() == DEGREE) {
					// Write bRight to file
					// Write bLeft to file
					balance(parent);
					return;
				} else
					return;
			}
		}
	}

	/**
	 * This function will print the leaf nodes of the B+Tree. This was written
	 * just for debugging purposes.
	 * 
	 * @author Yogeshwara Krishnan
	 * @param pNode
	 */
	static void printLeafNodes(BTreeNode pNode) {
		while (!pNode.isLeaf) {
			printLeafNodes(pNode.ptr.get(0));
			return;
		}

		if (pNode.isLeaf) {
			while (pNode.nextLeafPointer != null) {
				System.out.println(pNode.key + " : " + pNode.byteOffset + " : "
						+ pNode.dataLength);
				pNode = pNode.nextLeafPointer;
			}
			if (pNode.nextLeafPointer == null) {
				System.out.println(pNode.key + " : " + pNode.byteOffset + " : "
						+ pNode.dataLength);
			}
		}
	}

	/**
	 * This function reads the index file specified as argument and gets 3K of
	 * data from it.
	 * 
	 * @param indexFile
	 *            is the index file to be read.
	 * @param pSearchKey
	 *            is the key to be searched.
	 * @author Prabhmanmeet Singh
	 */
	static void readIndexForSearch(String indexFile, String pSearchKey) {
		// Temporary variable which acts as a root for each 3K block being read.
		BTreeNode newRoot = null;
		try {
			FileInputStream fin = new FileInputStream(indexFile);
			FileChannel fc = fin.getChannel();
			fc.position(1025l); // Because metadata is of 1024 bytes.
			ObjectInputStream ois = new ObjectInputStream(fin);
			// Reading Root node from file.
			newRoot = (BTreeNode) ois.readObject();
			ois.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		// Search for the key in the currently read tree.
		if (newRoot != null) {
			searchData(newRoot, indexFile, pSearchKey);
		}
	}

	/**
	 * This function reads the index file specified as argument and gets 3K of
	 * data from it.
	 * 
	 * @param indexFile
	 *            is the index file to be read.
	 * @param pSearchKey
	 *            is the search key which has to be found
	 * @param pListSize
	 *            is the no. of elements after pSearchKey which need to be
	 *            listed including pSearchKey.
	 * @author Yogeshwara Krishnan
	 */
	static void readIndexForListSearch(String indexFile, String pSearchKey,
			String pListSize) {
		BTreeNode newRoot = null;
		int listSize = Integer.parseInt(pListSize);
		try {
			FileInputStream fin = new FileInputStream(indexFile);
			FileChannel fc = fin.getChannel();
			fc.position(1025l);
			ObjectInputStream ois = new ObjectInputStream(fin);
			// Reading Root node from file.
			newRoot = (BTreeNode) ois.readObject();
			ois.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		if (newRoot != null) {
			searchListData(newRoot, indexFile, pSearchKey, listSize);
		}
	}

	/**
	 * A recursive function which traverses 3K block of the BTree read from the
	 * index file and searches for the key.
	 * 
	 * @param pNode
	 *            is the node being searched for the key.
	 * @param pSearchKey
	 *            is the key being searched.
	 * @author Prabhmanmeet Singh
	 */
	static void searchData(BTreeNode pNode, String indexFile, String pSearchKey) {
		for (int count = 0; count < pNode.key.size(); count++) {
			// Check if the node is a leaf node.
			if (pNode.isLeaf) {
				int keyIndex = pNode.key.indexOf(pSearchKey);
				if (keyIndex == -1) { // If key isn't present.
					System.out.println("Data not found");
					return;
				} else if (keyIndex != -1) { // If key is found.
					// Byte offset of the key in the input file.
					long byteOffset = pNode.byteOffset.get(keyIndex);
					// Length of the data to be read.
					int dataLength = pNode.dataLength.get(keyIndex);
					// Traverse to that node and read the input file
					getSearchData(indexFile, byteOffset, dataLength);
					return;
				}
			}

			// Traversing the left subtree.
			else if (pSearchKey.compareTo(pNode.key.get(count)) < 0) {
				if (!pNode.isLeaf && pNode.ptr.get(count) != null) {
					searchData(pNode.ptr.get(count), indexFile, pSearchKey);
					return;
				}
			}

			// Traversing the right subtree.
			else if (pSearchKey.compareTo(pNode.key.get(count)) >= 0) {
				if (count < pNode.key.size() - 1) {
					continue;
				}

				else if (count == pNode.key.size() - 1) {
					if (!pNode.isLeaf && pNode.ptr.get(count + 1) != null) {
						searchData((BTreeNode) pNode.ptr.get(count + 1),
								indexFile, pSearchKey);
						return;
					}
				}
			}
		}
	}

	/**
	 * A recursive function which traverses the B+ tree and retrieves list of
	 * data starting from pSearchKey.
	 * 
	 * @param pNode
	 *            is the node where we start our search.
	 * @param indexFile
	 *            is the index file we need to read.
	 * @param pSearchKey
	 *            is the key we need to search for.
	 * @param listSize
	 *            is the no. of records to be retrieved after the pSearchKey
	 *            (including).
	 * @author Yogeshwara Krishnan
	 */
	static void searchListData(BTreeNode pNode, String indexFile,
			String pSearchKey, int listSize) {
		for (int count = 0; count < pNode.key.size(); count++) {
			// Check if the node is a leaf node.
			if (pNode.isLeaf) {
				int keyIndex = pNode.key.indexOf(pSearchKey);
				if (keyIndex == -1) { // If key not found.
					System.out.println("Data not found");
					return;
				} else if (keyIndex != -1) { // If key was found.
					// Get the byte offset.
					long byteOffset = pNode.byteOffset.get(keyIndex);
					// Get the no of bytes to be read.
					int dataLength = pNode.dataLength.get(keyIndex);
					// Read the input file and print the data
					getSearchData(indexFile, byteOffset, dataLength);
					int ct = 2;
					// Iterate through the node to retrieve other keys.
					for (int i = keyIndex + 1; i < pNode.key.size(); i++, ct++) {
						if (ct <= listSize)
							getSearchData(indexFile, pNode.byteOffset.get(i),
									pNode.dataLength.get(i));
					}
					
					// If the current node is exhausted and target size hasn't been reached
					// go to the next leaf pointer.
					BTreeNode nextLeaf = pNode.nextLeafPointer;
					while (nextLeaf != null) {
						for (int i = 0; i < nextLeaf.key.size(); i++, ct++) {
							if (ct <= listSize)
								getSearchData(indexFile,
										nextLeaf.byteOffset.get(i),
										nextLeaf.dataLength.get(i));
						}
						nextLeaf = nextLeaf.nextLeafPointer;
					}
					return;
				}
			}

			// Traverse through the left sub tree.
			else if (pSearchKey.compareTo(pNode.key.get(count)) < 0) {
				if (!pNode.isLeaf && pNode.ptr.get(count) != null) {
					searchListData(pNode.ptr.get(count), indexFile, pSearchKey,
							listSize);
					return;
				}
			}

			// Traverse through the right sub tree.
			else if (pSearchKey.compareTo(pNode.key.get(count)) >= 0) {
				if (count < pNode.key.size() - 1) {
					continue;
				}

				else if (count == pNode.key.size() - 1) {
					if (!pNode.isLeaf && pNode.ptr.get(count + 1) != null) {
						searchListData((BTreeNode) pNode.ptr.get(count + 1),
								indexFile, pSearchKey, listSize);
						return;
					}
				}
			}
		}
	}

	/**
	 * This method prints the data which was found while searching B+ Tree.
	 * We do a random access on the file using the byte offset and the data length
	 * variables.
	 * @param indexFile is the index file to be read.
	 * @param byteOffset is the byte offset which will be used for random access.
	 * @param dataLength is the no of bytes to be read starting from byteOffset.
	 * @author Prabhmanmeet Singh
	 */
	static void getSearchData(String indexFile, long byteOffset, int dataLength) {
		RandomAccessFile file = null;
		// Get the file name which was index from the metadata.
		String inputFileName = getInputFileNameFromMetadata(indexFile);
		try {
			file = new RandomAccessFile(inputFileName, "r");
			file.seek(byteOffset); // Place the file pointer at this offset.
			byte buffer[] = new byte[dataLength];
			file.read(buffer); // Read the data.

			String str = new String(buffer);
			System.out.println(str);
		} catch (Exception e) {
			System.out.println(e.getStackTrace());
		} finally {
			try {
				file.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	/**
	 * This method inserts the new data into the input file.
	 * @param indexFile is the index file to be read.
	 * @param pData is the new data to be inserted into the tree 
	 *        and the file that was indexed.
	 * @author Yogeshwara Krishnan 
	 */
	static void insertNewData(String indexFile, String pData) {
		int keyLength = Integer.parseInt(getKeyLengthFromMetadata(indexFile));
		// Extract the key from the data.
		String key = (String) pData.subSequence(0, keyLength);
		// Check if the key exists.
		readIndexBeforeInsert(indexFile, key, pData);
	}

	/**
	 * This function will read the index file and searches for the key which
	 * must be inserted. This is for sanity check.
	 * @param indexFile is the index file to be read to get the 3K block of the tree.
	 * @param pSearchKey is the key that must be searched before inserting.
	 * @param pData is the data that must be added.
	 * @author Prabhmanmeet Singh
	 */
	static void readIndexBeforeInsert(String indexFile, String pSearchKey,
			String pData) {
		BTreeNode newRoot = null;
		try {
			FileInputStream fin = new FileInputStream(indexFile);
			FileChannel fc = fin.getChannel();
			fc.position(1025l); // Because metadata is 1024 bytes.
			ObjectInputStream ois = new ObjectInputStream(fin);
			// Reading Root node from file.
			newRoot = (BTreeNode) ois.readObject();
			ois.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		if (newRoot != null) { // Search for the data.
			searchDataBeforeInsert(newRoot, indexFile, pSearchKey, pData);
		}
	}

	
	/**
	 * Recursive function which traverses the given sub tree and searches for the key.
	 * If not found, we will insert it.
	 * @param pNode is the node being traversed.
	 * @param indexFile is the index file being read.
	 * @param pSearchKey is the key which must be found before adding it to B+ Tree.
	 * @param pData is the data to be added.
	 * @author Yogeshwara Krishnan
	 */
	static void searchDataBeforeInsert(BTreeNode pNode, String indexFile,
			String pSearchKey, String pData) {
		for (int count = 0; count < pNode.key.size(); count++) {
			// Check if the node is a leaf node.
			if (pNode.isLeaf) {
				int keyIndex = pNode.key.indexOf(pSearchKey);
				if (keyIndex == -1) { // When data wasn't found. Add the data at
										// the end of the file.
					// System.out.println("Data not found");
					String inputFileName = getInputFileNameFromMetadata(indexFile);

					// Append the data to the text file.
					int fileOffset = updateInputFile(inputFileName, pData);
					// Update the index file.
					updateBTree(inputFileName, indexFile, pSearchKey,
							fileOffset, pData.length() + 1,
							getKeyLengthFromMetadata(indexFile));
					return;
				} else if (keyIndex != -1) {
					System.out
							.println("Data already exists. Duplicates not allowed");
					return;
				}
			}

			// Traverse the left sub tree.
			else if (pSearchKey.compareTo(pNode.key.get(count)) < 0) {
				if (!pNode.isLeaf && pNode.ptr.get(count) != null) {
					searchDataBeforeInsert(pNode.ptr.get(count), indexFile,
							pSearchKey, pData);
					return;
				}
			}

			// Traverse the right sub tree.
			else if (pSearchKey.compareTo(pNode.key.get(count)) >= 0) {
				if (count < pNode.key.size() - 1) {
					continue;
				}

				else if (count == pNode.key.size() - 1) {
					if (!pNode.isLeaf && pNode.ptr.get(count + 1) != null) {
						searchDataBeforeInsert(
								(BTreeNode) pNode.ptr.get(count + 1),
								indexFile, pSearchKey, pData);
						return;
					}
				}
			}
		}
	}

	/**
	 * This method updates the index file.
	 * @param inputFile is the input file to be updated.
	 * @param indexFile is the index file to be updated.
	 * @param pSearchKey is the key to be added to the B+ tree.
	 * @param fileOffset is the file offset where the key must be added.
	 * @param length is the length of the data being added.
	 * @param pkeyLength is the length of the key got from metadata.
	 * @author Prabhmanmeet Singh
	 */
	private static void updateBTree(String inputFile, String indexFile,
			String pSearchKey, int fileOffset, int length, String pkeyLength) {

		BTreeNode newRoot = null;
		try {
			FileInputStream fin = new FileInputStream(indexFile);
			FileChannel fc = fin.getChannel();
			fc.position(1025l);
			ObjectInputStream ois = new ObjectInputStream(fin);
			newRoot = (BTreeNode) ois.readObject();
			ois.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		root = newRoot;
		insertIntoBTree(newRoot, pSearchKey, fileOffset, length);

		try {
			FileOutputStream fout = new FileOutputStream(indexFile);
			FileChannel fc = fout.getChannel();
			// Re-write the metadata to be sure.
			byte[] inputFileName = inputFile.getBytes();
			byte[] keyLength = pkeyLength.getBytes();
			byte[] rootOffset = (" " + root.key.get(0)).getBytes();
			fc.write(ByteBuffer.wrap(inputFileName));
			fc.write(ByteBuffer.wrap(keyLength), 257l);
			fc.write(ByteBuffer.wrap(rootOffset), 260l);
			fc.position(1025l);
			ObjectOutputStream oos = new ObjectOutputStream(fout);
			oos.writeObject(root);
			oos.close();
			// System.out.println("Done");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	
	/**
	 * This method inserts the new data after checking the index
	 * if it exists or not.
	 * @param inputFile is the input file to be updated
	 * @param pData is the data to be added.
	 * @return fileOffset is the offset where the new key was added.
	 * @author Yogeshwara Krishnan
	 */
	private static int updateInputFile(String inputFile, String pData) {
		File inFile = new File(inputFile);
		int fileOffset = 0;
		if (inFile.exists()) {
			fileOffset = (int) inFile.length(); // End of the file.
		}

		RandomAccessFile file = null;
		try {
			file = new RandomAccessFile(inputFile, "rw");
			file.seek(fileOffset - 1); // Check if the eof is new line or not.

			byte[] buffer = new byte[1];
			file.read(buffer);
			if ((int) buffer[0] == 10) {
				file.seek(fileOffset);
				file.writeBytes(pData + "\n");
			} else {
				file.seek(fileOffset);
				file.writeBytes("\n" + pData);
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		} finally {
			try {
				file.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

		return fileOffset; // return the file offset to update the B+ Tree.
	}

	/**
	 * This method writes the 3K nodes in the memory to the index file.
	 * @param pKeyLength is the length of the key.
	 * @param inputFile is the input file to be read.
	 * @param outputFile is the name of the index file.
	 * @author Prabhmanmeet Singh
	 */
	private static void createIndexFile(String pKeyLength, String inputFile,
			String outputFile) {
		BufferedReader br = null;
		int byteOffset = 0;
		int keyLength = Integer.parseInt(pKeyLength);

		try {
			String sCurrentLine;
			br = new BufferedReader(new FileReader(inputFile));
			while ((sCurrentLine = br.readLine()) != null) {
				insertIntoBTree(root,
						(String) sCurrentLine.subSequence(0, keyLength),
						byteOffset, sCurrentLine.length());
				byteOffset += sCurrentLine.length() + 2;
			}
		} catch (IOException e) {
			System.out.println("File " + inputFile + " not found.");
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

		writetoIndexFile(pKeyLength, inputFile, outputFile);
	}

	/**
	 * This method writes the B+Tree into a file.
	 * @param pKeyLength is the length of the key.
	 * @param inputFile is the input file name.
	 * @param outputFile is the output file name.
	 * @author Yogeshwara Krishnan
	 */
	private static void writetoIndexFile(String pKeyLength, String inputFile,
			String outputFile) {
		try {
			FileOutputStream fout = new FileOutputStream(outputFile);
			// Write the metadata. Metadata includes the file name being
			// indexed, the key length, offset of the root.
			byte[] inputFileName = inputFile.getBytes();
			byte[] keyLength = pKeyLength.getBytes();
			byte[] rootOffset = (" " + root.key.get(0)).getBytes();
			FileChannel fc = fout.getChannel();
			fc.write(ByteBuffer.wrap(inputFileName));
			fc.write(ByteBuffer.wrap(keyLength), 257l);
			fc.write(ByteBuffer.wrap(rootOffset), 260l);
			fc.position(1025l);
			ObjectOutputStream oos = new ObjectOutputStream(fout);
			oos.writeObject(root);
			oos.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * This methods reads the metadata from the index file
	 * and returns the name of the file which was indexed. 
	 * @param outputFile is the index file.
	 * @return the file name which was indexed.
	 * @author Yogeshwara Krishnan
	 */
	static String getInputFileNameFromMetadata(String outputFile) {

		RandomAccessFile file = null;
		String inputFileName = "";
		try {
			file = new RandomAccessFile(outputFile, "r");
			byte[] inputFileByte = new byte[256];
			file.read(inputFileByte);
			inputFileName = new String(inputFileByte);
		} catch (Exception e) {
			System.out.println(e.getLocalizedMessage());
		}

		return inputFileName.trim();
	}

	
	/**
	 * This method reads the index file metadata and returns the key length.
	 * @param indexFile
	 * @return the length of the key.
	 * @author Prabhmanmeet Singh
	 */
	static String getKeyLengthFromMetadata(String indexFile) {
		RandomAccessFile file = null;
		String keyLength = "";
		try {
			file = new RandomAccessFile(indexFile, "r");
			byte[] keyLengthByte = new byte[3];
			file.seek(257l);
			file.read(keyLengthByte);
			keyLength = new String(keyLengthByte);
		} catch (Exception e) {
			System.out.println(e.getStackTrace());
		}

		return keyLength.trim();
	}

	
	/**
	 * This method calculates the degree of the tree.
	 * @param pKeyLength is the length of the key
	 */
	private static void calculateDegree(String pKeyLength) {
		int keyLength = Integer.parseInt(pKeyLength);

		DEGREE = (1024 - 8) / (keyLength + 8 + 8);

	}

	// Main method.
	public static void main(String[] args) {
		root = new BTreeNode();
		// To make sure, upper and lower case do not make a difference.
		String operation = args[0].toLowerCase();

		if ("-create".compareTo(operation) == 0) {
			System.out.println("Create");
			calculateDegree(args[1]);
			createIndexFile(args[1], args[2], args[3]);
			getInputFileNameFromMetadata(args[3]);
		}

		else if ("-find".compareTo(operation) == 0) {
			System.out.println("Find");
			readIndexForSearch(args[1], args[2]);
		}

		else if ("-insert".compareTo(operation) == 0) {
			System.out.println("Insert");
			insertNewData(args[1], args[2]);
		}

		else if ("-list".compareTo(operation) == 0) {
			System.out.println("List");
			readIndexForListSearch(args[1], args[2], args[3]);
		}

		System.out.println("Success");
	}
}