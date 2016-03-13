package engine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class BPlusTree<E> implements Serializable{
	private int n;
	private Node root;
	private int[][] rules;
	private final int NON_LEAF = 0;
	private final int LEAF = 1;
	private final int ROOT = 2;
	private final int MAX_PTRS = 0;
	private final int MAX_KEYS = 1;
	private final int MIN_PTRS = 2;
	private final int MIN_KEYS = 3;

	public BPlusTree(int n) {
		this.n = n;
		this.root = new Node<>(n, true);
		rules = new int[3][4];
		for (int i = 0; i < 3; i++)
			rules[i][MAX_PTRS] = n+1;

		for (int i = 0; i < 3; i++)
			rules[i][MAX_KEYS] = n;

		rules[NON_LEAF][MIN_PTRS] = ceil((double)(n+1)/2);
		rules[LEAF][MIN_PTRS] = floor((double)(n+1)/2);
		rules[ROOT][MIN_PTRS] = 1;

		rules[NON_LEAF][MIN_KEYS] = ceil((double)(n+1)/2) - 1;
		rules[LEAF][MIN_KEYS] = floor((double)(n+1)/2);
		rules[ROOT][MIN_KEYS] = 1;
	}

	public ArrayList<Record> find(E target) {
		Queue<Node> q = new LinkedList<Node>();
		q.add(root);

		while(!q.isEmpty()) {
			Node node = q.poll();
			if (node == null)
				continue;
			if (node.isLeaf) {
				Object key = node.search(target);
				if(key instanceof ArrayList)
					return (ArrayList<Record>) key;
				else{
					ArrayList<Record> res = new ArrayList<>();
					res.add((Record) key);
					return res;
				}
			}
			else 
				q.add((Node)node.search(target));
		}

		return null;
	}

	public void insert(E value, String page, int index) {
		Record record = new Record(page, index);
		Node newNode = insertHelper(root, value, record);

		if (newNode == null)
			return;

		Comparable<E> newMin = newNode.keys[newNode.keys.length - 1];

		Node newRoot = new Node<>(n, false);
		newRoot.no_of_keys++;
		newRoot.keys[0] = newMin;
		newRoot.pointers[0] = root;
		newRoot.pointers[1] = newNode;
		root = newRoot;
	}

	public Node insertHelper(Node current, E value, Record record) {
		if (current.isLeaf) {
			boolean inserted = current.insertInLeaf(value, record);

			if (inserted)
				return null;

			Node newNodeOfMe = current.migrateLeaf((n % 2 == 0)? rules[LEAF][MIN_KEYS] + 1 : rules[LEAF][MIN_KEYS]);
			newNodeOfMe.keys[newNodeOfMe.keys.length - 1] = newNodeOfMe.keys[0];
			newNodeOfMe.pointers[newNodeOfMe.pointers.length - 1] = current;

			return newNodeOfMe;
		}
		else {
			Node newNode = insertHelper((Node) current.search(value), value, record);

			if (newNode == null)
				return null;

			Comparable<E> newMin = newNode.keys[newNode.keys.length - 1];
			Node missingPointer = (Node) newNode.pointers[newNode.pointers.length - 1];

			boolean inserted = current.insertInNonLeaf(newMin, missingPointer, newNode);

			if (inserted)
				return null;

			Node newNodeOfMe = current.migrateNonLeaf((n % 2 == 0)? rules[NON_LEAF][MIN_KEYS] : rules[NON_LEAF][MIN_KEYS] + 1);
			newNodeOfMe.keys[newNodeOfMe.keys.length - 1] = current.keys[current.no_of_keys - 1];
			newNodeOfMe.pointers[newNodeOfMe.pointers.length - 1] = current;
			current.no_of_keys--;

			return newNodeOfMe;
		}
	}

	public void delete(E value) {
		boolean deleted = deleteHelper(root, value);

		if (deleted)
			return;

		// new root
		if (root.no_of_keys < rules[ROOT][MIN_KEYS]) 
			root = root.pointers[0] instanceof Record || root.pointers[0] instanceof ArrayList ? null : (Node) root.pointers[0];
	}

	public boolean deleteHelper(Node node, E value) {
		if (node.isLeaf) {
			int index = Arrays.binarySearch(node.keys, value);

			if (index == -1)
				return true;

			node.deleteFromNode(index);

			if (node == root)
				return false;

			if (node.no_of_keys < rules[LEAF][MIN_KEYS]) {
				Node parent = (Node) node.pointers[node.pointers.length - 1];
				int separatorIndex = (int) node.keys[node.keys.length - 1];

				Node leftNode = null;
				if (separatorIndex > -1) {
					leftNode = (Node) parent.pointers[separatorIndex];
					if (leftNode.no_of_keys > rules[LEAF][MIN_KEYS]) {
						node.rotateRight(leftNode);
						return true;
					}
				}

				separatorIndex++;
				node.keys[node.keys.length - 1] =  (int) node.keys[node.keys.length - 1] + 1;

				Node rightNode = null;
				if (separatorIndex < parent.no_of_keys) {
					rightNode = (Node) parent.pointers[separatorIndex + 1];
					if (rightNode.no_of_keys > rules[LEAF][MIN_KEYS]) {
						node.rotateLeft(rightNode);
						return true;
					}
				}

				//merge
				if (leftNode == null) { // merge with right, you are the left
					node.keys[node.keys.length - 1] = separatorIndex;
					node.merge(rightNode, false);
				}
				else { // merge with left, you are the right
					node.keys[node.keys.length - 1] = separatorIndex - 1;
					leftNode.merge(node, true);
				}

				return false;
			}

			Node parent = (Node) node.pointers[node.pointers.length - 1];
			int separatorIndex = (int) node.keys[node.keys.length - 1];
			if (separatorIndex > -1)
				parent.keys[separatorIndex] = node.keys[0];
			return true;
		}
		else {
			int low = 0;
			int high = node.no_of_keys - 1;

			Node nextNode = null;
			int separatorIndex = -1;
			while(low <= high){
				int mid = low + (high - low) / 2;

				if(node.keys[mid].compareTo(value) == 0) {
					nextNode = (Node) node.pointers[mid + 1];
					separatorIndex = mid;
					break;
				}
				else if(node.keys[mid].compareTo(value) < 0)
					low = mid + 1;
				else
					high = mid - 1;
			}

			if (nextNode == null) {
				nextNode = (Node) node.pointers[high + 1];
				separatorIndex = high;
			}

			nextNode.pointers[nextNode.pointers.length - 1] = node;
			nextNode.keys[nextNode.keys.length - 1] = separatorIndex;

			boolean deleted = deleteHelper(nextNode, value);

			if (deleted) 	
				return true;

			separatorIndex = (int) nextNode.keys[nextNode.keys.length - 1];

			node.deleteFromNode(separatorIndex);

			if (node == root)
				return false;

			if (node.no_of_keys < rules[NON_LEAF][MIN_KEYS]) {
				Node parent = (Node) node.pointers[node.pointers.length - 1];
				separatorIndex = (int) node.keys[node.keys.length - 1];

				Node leftNode = null;
				if (separatorIndex > -1) {
					leftNode = (Node) parent.pointers[separatorIndex];
					if (leftNode.no_of_keys > rules[NON_LEAF][MIN_KEYS]) {
						node.rotateRight(leftNode);
						return true;
					}
				}

				separatorIndex++;
				node.keys[node.keys.length - 1] =  (int) node.keys[node.keys.length - 1] + 1;

				Node rightNode = null;
				if (separatorIndex < parent.no_of_keys) {
					rightNode = (Node) parent.pointers[separatorIndex + 1];
					if (rightNode.no_of_keys > rules[NON_LEAF][MIN_KEYS]) {
						node.rotateLeft(rightNode);
						return true;
					}
				}

				//merge
				if (leftNode == null) { // merge with right, you are the left
					node.keys[node.keys.length - 1] = separatorIndex;
					node.merge(rightNode, false);
				}
				else { // merge with left, you are the right
					node.keys[node.keys.length - 1] = separatorIndex - 1;
					leftNode.merge(node, true);
				}

				return false;
			}

			Node parent = (Node) node.pointers[node.pointers.length - 1];
			separatorIndex = (int) node.keys[node.keys.length - 1];
			if (separatorIndex > -1)
				parent.keys[separatorIndex] = node.keys[0];
			return true;
		}
	}

	public static int ceil(double d) {
		return (int) (d == (int)d ? d : ((int)d)+1);
	}

	public static int floor(double d) {
		return (int) d;
	}

	public String toString() {
		String res = "";
		if (root == null)
			return res;

		Queue<Node> q = new LinkedList<Node>();
		q.add(root);
		int c = 1;
		int tempC = 0;

		while(!q.isEmpty()) {
			Node current = q.remove();
			if (c == 0 || (c == 1 && current != root)) {
				res += '\n';
				c = tempC;
				tempC = 0;
			}
			else
				c--;
			res += current.toString() + " ";

			if (!current.isLeaf) {
				for (int i = 0; i <= current.no_of_keys; i++) {
					Node n = (Node) current.pointers[i];
					q.add(n);
					tempC++;
				}
			}



		}

		return res;
	}

	private static class Node<E>  implements Serializable{
		private Comparable<E>[] keys;
		private Object[] pointers;
		private Node magicPointer;
		private boolean isLeaf;
		private int no_of_keys;

		public Node(int n, boolean isLeaf) {
			this.keys = new Comparable[n + 1];
			this.pointers = new Object[n + 3];
			this.magicPointer = null;
			this.isLeaf = isLeaf;
			this.no_of_keys = 0;
		}


		//Method used for insertion and find
		public Object search(E target) {
			return this.isLeaf ? searchLeaf(target) : searchNonLeaf(target);
		}

		public Node searchNonLeaf(E target){
			int low = 0;
			int high = no_of_keys - 1;

			while(low <= high){
				int mid = low + (high - low) / 2;

				if(keys[mid].compareTo(target) == 0)
					return (Node) pointers[mid + 1];
				else if(keys[mid].compareTo(target) < 0)
					low = mid + 1;
				else
					high = mid - 1;
			}

			return (Node) pointers[high + 1];
		}

		public Object searchLeaf(E target){
			int low = 0;
			int high = no_of_keys - 1;

			while(low <= high){
				int mid = low + (high - low) / 2;

				if(keys[mid].compareTo(target) == 0)
					return pointers[mid];
				else if(keys[mid].compareTo(target) < 0)
					low = mid + 1;
				else
					high = mid - 1;
			}

			return null;
		}

		public boolean insertInLeaf(E value, Record record) {
			boolean valid = true;

			if (no_of_keys == keys.length - 1)
				valid = false;


			int i;
			for (i = no_of_keys; i >= 1; i--) {
				if (keys[i - 1].compareTo(value) > 0){
					keys[i] = keys[i - 1];
					pointers[i] = pointers[i - 1];
				}
				else if(keys[i - 1].compareTo(value) == 0) {
					if(!(pointers[i - 1] instanceof ArrayList)){
						Record temp = (Record) pointers[i - 1];
						pointers[i - 1] = (Object) (new ArrayList<>());
						((ArrayList<Record>) pointers[i - 1]).add(temp);
					}

					((ArrayList<Record>) pointers[i - 1]).add(record);

					for (int j = i; j < no_of_keys; j++) {
						keys[j] = keys[j + 1];
						pointers[j] = pointers[j + 1];
					}

					return true;
				}
				else {
					keys[i] = (Comparable<E>) value;
					pointers[i] = record;
					break;
				}
			}

			no_of_keys++;
			if(i == 0){
				keys[0] = (Comparable<E>) value;
				pointers[0] = (Object) record;
			}

			return valid;
		}

		public boolean insertInNonLeaf(E value, Node missingPointer, Node newNode) {
			boolean valid = true;

			if (no_of_keys == keys.length - 1)
				valid = false;


			int i;
			for (i = no_of_keys; i >= 1; i--) {
				if (keys[i - 1].compareTo(value) > 0){
					keys[i] = keys[i - 1];
					pointers[i + 1] = pointers[i];
				}
				else {
					keys[i] = (Comparable<E>) value;
					pointers[i + 1] = newNode;
					pointers[i] = missingPointer;
					break;
				}
			}

			no_of_keys++;
			if(i == 0){
				keys[0] = (Comparable<E>) value;
				pointers[1] = newNode;
				pointers[0] = missingPointer;
			}

			return valid;
		} 

		public Node migrateLeaf(int k){
			Node newNode = new Node<>(keys.length - 1, isLeaf);// keys.length - 1

			for (int i = keys.length - k/*no_of_keys - 1*/, j = 0; i  <= no_of_keys - 1/*>= keys.length - k*/; i++, j++) {
				newNode.keys[j] = keys[i];
			}

			for (int i = keys.length - k/*no_of_keys*/, j = 0; i < no_of_keys/*>= keys.length - (k + 1)*/; i++, j++) {
				newNode.pointers[j] = pointers[i];
			}

			no_of_keys -= k;
			newNode.no_of_keys = k;
			newNode.magicPointer = magicPointer;
			magicPointer = newNode;

			return newNode;
		}

		public Node migrateNonLeaf(int k){
			Node newNode = new Node<>(keys.length - 1, isLeaf);

			for (int i = keys.length - k/*no_of_keys - 1*/, j = 0; i  <= no_of_keys - 1/*>= keys.length - k*/; i++, j++) {
				newNode.keys[j] = keys[i];
			}

			for (int i = keys.length - k/*no_of_keys*/, j = 0; i <= no_of_keys/*>= keys.length - (k + 1)*/; i++, j++) { // the problem is here
				newNode.pointers[j] = pointers[i];
			}

			no_of_keys -= k;
			newNode.no_of_keys = k;

			return newNode;
		}

		//Methods used for deletion
		public void deleteFromNode(int index) {

			for (int i = index + 1; i < no_of_keys; i++) 
				keys[i - 1] = keys[i];

			int extra = 0;
			if (!isLeaf)
				extra++;

			for (int i = index + 1 + extra; i < no_of_keys + extra; i++)
				pointers[i - 1] = pointers[i];

			no_of_keys--;
		}


		public void rotateLeft(Node rightNode) {
			Node parent = (Node) pointers[pointers.length - 1];
			int separatorIndex = (int) keys[keys.length - 1];

			Node leftNode = this;

			//rotate the needed key with its pointers
			if(leftNode.isLeaf){
				leftNode.keys[leftNode.no_of_keys] = rightNode.keys[0];
				leftNode.pointers[leftNode.no_of_keys++] = rightNode.pointers[0];
			}
			else{
				leftNode.keys[leftNode.no_of_keys] = parent.keys[separatorIndex];
				leftNode.pointers[++leftNode.no_of_keys] = rightNode.pointers[0];
			}

			//change the separator
			parent.keys[separatorIndex] = rightNode.keys[0];

			//shift the elements in the right sibling with their pointers
			for (int i = 1; i < rightNode.no_of_keys; i++)
				rightNode.keys[i - 1] = rightNode.keys[i];

			int extra = 0;
			if(!leftNode.isLeaf)
				extra++;

			for (int i = 1; i < rightNode.no_of_keys + extra; i++) 
				rightNode.pointers[i - 1] = rightNode.pointers[i];

			rightNode.no_of_keys--;

		}

		public void rotateRight(Node leftNode) {
			Node parent = (Node) pointers[pointers.length - 1];
			int separatorIndex = (int) keys[keys.length - 1];

			Node rightNode = this;

			//shift the elements in the right sibling with their pointers
			for (int i = rightNode.no_of_keys; i > 0; i--)
				rightNode.keys[i] = rightNode.keys[i - 1];

			int extra = 0;
			if (!rightNode.isLeaf)
				extra++;

			for (int i = rightNode.no_of_keys + extra; i > 0; i--)
				rightNode.pointers[i] = rightNode.pointers[i - 1];

			rightNode.no_of_keys++;

			//rotate the needed key with its pointers
			if(rightNode.isLeaf){
				rightNode.keys[0] = leftNode.keys[leftNode.no_of_keys - 1];
				rightNode.pointers[0] = leftNode.pointers[--leftNode.no_of_keys];
				parent.keys[separatorIndex] = rightNode.keys[0];
			}
			else{
				//				rightNode.keys[0] = parent.keys[separatorIndex];
				rightNode.pointers[0] = leftNode.pointers[leftNode.no_of_keys];
				parent.keys[separatorIndex] = leftNode.keys[--leftNode.no_of_keys];
			}

		}

		public void merge(Node rightNode, boolean flag) {
			Node n = flag ? rightNode : this;

			Node parent = (Node) n.pointers[pointers.length - 1];
			int separatorIndex = (int) n.keys[keys.length - 1];

			Node leftNode = this;

			if (leftNode.isLeaf) {
				//migrate the values to the leftNode
				for (int i = 0; i < rightNode.no_of_keys; i++) 
					leftNode.keys[leftNode.no_of_keys + i] = rightNode.keys[i];

				//migrate the pointers to the leftNode
				for (int i = 0; i < rightNode.no_of_keys; i++)
					leftNode.pointers[leftNode.no_of_keys++] = rightNode.pointers[i];

				leftNode.magicPointer = rightNode.magicPointer;
			}
			else {
				leftNode.keys[leftNode.no_of_keys++] = parent.keys[separatorIndex];
				leftNode.pointers[leftNode.no_of_keys] = rightNode.pointers[0];

				for (int i = 0; i < rightNode.no_of_keys; i++)
					leftNode.keys[leftNode.no_of_keys + i] = rightNode.keys[i];

				for (int i = 1; i <= rightNode.no_of_keys; i++)
					leftNode.pointers[++leftNode.no_of_keys] = rightNode.pointers[i];
			}



		}


		// Methods used for printing
		public String toString() {
			String res = "[";
			for (int i = 0; i < keys.length - 1; i++) {
				Comparable<E> e = keys[i];
				if (i > 0) {
					res += "|";
				}

				if (i < no_of_keys)
					if(isLeaf && pointers[i] instanceof ArrayList){

						res += "{";
						for (int j = 0; j < ((ArrayList<Record>) pointers[i]).size() - 1; j++) {
							res += e.toString() + ", ";
						}

						res += e.toString() + "}";
					}
					else
						res += e.toString();
				else
					res += null;
			}
			res += ']';
			return res;
		}
	}
	
	public static void main(String[] args) {
		BPlusTree<Integer> b = new BPlusTree<>(2);
		b.insert(12, "dummy.class", 0);
		b.insert(8, "dummy.class", 0);
		b.insert(1, "dummy.class", 0);
		b.insert(23, "dummy.class", 0);
		b.insert(5, "dummy.class", 0);
		b.insert(23, "dummy.class", 0);
		b.insert(5, "dummy.class", 0);
		b.insert(23, "dummy.class", 0);
		b.insert(5, "dummy.class", 0);
		b.insert(23, "dummy.class", 0);
		b.insert(5, "dummy.class", 0);
		b.insert(7, "dummy.class", 0);
		b.insert(2, "dummy.class", 0);
		b.insert(28, "dummy.class", 0);
		b.insert(9, "dummy.class", 0);
		b.insert(18, "dummy.class", 0);
		b.insert(24, "dummy.class", 0);
		b.insert(40, "dummy.class", 0);
		b.insert(48, "dummy.class", 0);
		b.delete(48);
		b.delete(40);
		b.delete(2);
		b.delete(18);
		b.delete(12);
		b.delete(9);
		b.delete(5);
//		b.delete(23);
		b.delete(24);
		b.delete(28);

		//		System.out.println(((Node)((Node)b.root.pointers[1]).pointers[1]));
		//		b.insert(3, "dummy.class", 0);
		//		System.out.println(Arrays.toString(((Node)((Node)b.root.pointers[1])).pointers));
		//		System.out.println();

		//		System.out.println(((Node)b.root.pointers[1]).no_of_keys);
//		System.out.println(b);
		
		System.out.println(b.find(7));

	}
}
