import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.nio.channels.FileChannel;

public class ReadFile {

	public static void main(String[] args) {
		BTreeNode newRoot = new BTreeNode();
		
		try {
			FileInputStream fin = new FileInputStream("output.indx");
			FileChannel fc = fin.getChannel();
			fc.position(1025l);
			ObjectInputStream ois = new ObjectInputStream(fin);
			newRoot = (BTreeNode) ois.readObject();
			ois.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

}
