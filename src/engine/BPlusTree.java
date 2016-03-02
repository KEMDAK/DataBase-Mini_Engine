package engine;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import javax.swing.JPopupMenu.Separator;

public class BPlusTree<E> {
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

	public Page find(E target) {
		Queue<Node> q = new LinkedList<Node>();
		q.add(root);

		while(!q.isEmpty()) {
			Node node = q.poll();
			if (node == null)
				continue;
			if (node.isLeaf) 
				return (Page) node.search(target);
			else 
				q.add((Node)node.search(target));
		}

		return null;
	}

	public void insert(E value, Page page) {
		Node newNode = insertHelper(root, value, page);

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

	public Node insertHelper(Node current, E value, Page page) {
		if (current.isLeaf) {
			boolean inserted = current.insertInLeaf(value, page);

			if (inserted)
				return null;

			Node newNodeOfMe = current.migrateLeaf((n % 2 == 0)? rules[LEAF][MIN_KEYS] + 1 : rules[LEAF][MIN_KEYS]);
			newNodeOfMe.keys[newNodeOfMe.keys.length - 1] = newNodeOfMe.keys[0];
			newNodeOfMe.pointers[newNodeOfMe.pointers.length - 1] = current;

			return newNodeOfMe;
		}
		else {
			Node newNode = insertHelper((Node) current.search(value), value, page);

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

	private static class Node<E> {
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

		public boolean insertInLeaf(E value, Page page) {
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
						Page temp = (Page) pointers[i - 1];
						pointers[i - 1] = (Object) (new ArrayList<>());
						((ArrayList<Page>) pointers[i - 1]).add(temp);
					}
					
					((ArrayList<Page>) pointers[i - 1]).add(page);
					
					for (int j = i; j < no_of_keys; j++) {
						keys[j] = keys[j + 1];
						pointers[j] = pointers[j + 1];
					}
					
					return true;
				}
				else {
					keys[i] = (Comparable<E>) value;
					pointers[i] = page;
					break;
				}
			}

			no_of_keys++;
			if(i == 0){
				keys[0] = (Comparable<E>) value;
				pointers[0] = (Object) page;
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

//		public void rotateLeft(Node rightNode) {
//			Node parent = (Node) pointers[pointers.length - 1];
//			int separatorIndex = (int) keys[keys.length - 1];
//
//			//rotate the needed key with its pointers
//			keys[no_of_keys] = parent.keys[separatorIndex];
//			if(isLeaf){
//				pointers[no_of_keys++] = rightNode.pointers[0];
//			}
//			else{
//				pointers[no_of_keys] = rightNode.pointers[0];
//				pointers[++no_of_keys] = rightNode.pointers[1];
//			}
//
//			//shift the elements in the right sibling with their pointers
//			for (int i = 1; i < rightNode.no_of_keys; i++){
//				rightNode.keys[i - 1] = rightNode.keys[i];
//			}
//			
//			if(isLeaf){
//				for (int i = 1; i < rightNode.no_of_keys; i++) {
//					rightNode.pointers[i - 1] = rightNode.pointers[i];
//				}
//			}
//			else{
//				for (int i = 2; i <= rightNode.no_of_keys; i++) {
//					rightNode.pointers[i - 2] = rightNode.pointers[i];
//				}
//			}
//			rightNode.no_of_keys--;
//			
//			//change the separator
//			parent.keys[separatorIndex] = rightNode.keys[0];
//		}

//		public void rotateRight(Node LeftNode) {
//			Node parent = (Node) pointers[pointers.length - 1];
//			int separatorIndex = (int) keys[keys.length - 1];
//
//			//rotate the needed key with its pointers
//			keys[no_of_keys] = parent.keys[separatorIndex];
//			if(isLeaf){
//				pointers[no_of_keys++] = rightNode.pointers[0];
//			}
//			else{
//				pointers[no_of_keys] = rightNode.pointers[0];
//				pointers[++no_of_keys] = rightNode.pointers[1];
//			}
//
//			//shift the elements in the right sibling with their pointers
//			for (int i = 1; i < rightNode.no_of_keys; i++){
//				rightNode.keys[i - 1] = rightNode.keys[i];
//			}
//			
//			if(isLeaf){
//				for (int i = 1; i < rightNode.no_of_keys; i++) {
//					rightNode.pointers[i - 1] = rightNode.pointers[i];
//				}
//			}
//			else{
//				for (int i = 2; i <= rightNode.no_of_keys; i++) {
//					rightNode.pointers[i - 2] = rightNode.pointers[i];
//				}
//			}
//			rightNode.no_of_keys--;
//			
//			//change the separator
//			parent.keys[separatorIndex] = rightNode.keys[0];
//		}
		
		public String toString() {
			String res = "[";
			for (int i = 0; i < keys.length - 1; i++) {
				Comparable<E> e = keys[i];
				if (i > 0) {
					res += "|";
				}

				if (i < no_of_keys)
					if(isLeaf && pointers[i] instanceof ArrayList){
//						res += ((ArrayList<Page>) pointers[i]).toString();
						
						res += "{";
						for (int j = 0; j < ((ArrayList<Page>) pointers[i]).size() - 1; j++) {
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
		BPlusTree<Integer> b = new BPlusTree<>(3);
		b.insert(2, new Page("dummy.class"));
		b.insert(1, new Page("dummy.class"));
		b.insert(3, new Page("dummy.class"));
		b.insert(4, new Page("dummy.class"));
		b.insert(5, new Page("dummy.class"));
		b.insert(0, new Page("dummy.class"));
		b.insert(6, new Page("dummy.class"));
		b.insert(7, new Page("dummy.class"));
		b.insert(9, new Page("dummy.class"));
		b.insert(3, new Page("dummy.class"));
		b.insert(6, new Page("dummy.class"));
		b.insert(-1, new Page("dummy.class"));
		b.insert(3, new Page("dummy.class"));
//				System.out.println(((Node)((Node)b.root.pointers[0]).pointers[0]).magicPointer.magicPointer.magicPointer.magicPointer.magicPointer);
//				System.out.println();

		System.out.println(b);
	}
}
