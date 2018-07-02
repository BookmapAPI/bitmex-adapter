package bitmexAdapter;

import java.util.ArrayList;
import java.util.List;

public class BracketOrder {

	private String parent;
	private List<String> children = new ArrayList<>();
	
	public BracketOrder(String parent, String child, String otherChild) {
		super();
		this.parent = parent;
		List<String> children = new ArrayList<>();
		children.add(child);
		children.add(otherChild);
		this.children = children;
	}

	public String getParent() {
		return parent;
	}

	public List<String> getChildren() {
		return children;
	}

	public void setParent(String parent) {
		this.parent = parent;
	}

	public void setChildren(List<String> children) {
		this.children = children;
	}
	
	public void addChild(String id) {
		this.children.add(id);
	}
	
}
