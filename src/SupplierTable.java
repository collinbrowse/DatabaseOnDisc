import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.Supplier;

/**
 * Created by cmbrow12 on 11/2/2015.
 */
public class SupplierTable {

    // Size of sno field in bytes
    public static final int SNOSIZE = Integer.SIZE / Byte.SIZE; // Gives it to use in bits, we want bytes

    public static final int SNAMELEN = 10;
    // Assuming char(10)
    public static final int SNAMESIZE = Character.SIZE / Byte.SIZE * SNAMELEN;  //java uses unicode so a character is always 2 bytes

    public static final int STATUSSIZE = Integer.SIZE / Byte.SIZE;

    public static final int SCITYLEN = 10;

    public static final int SCITYSIZE = Character.SIZE / Byte.SIZE * SCITYLEN;

    public static final int NEXTSIZE = Long.SIZE / Byte.SIZE;

    public static final int RECORDSIZE = SNOSIZE + SNAMESIZE + STATUSSIZE + SCITYSIZE + NEXTSIZE;

    public static final int NEXTOFFSET = RECORDSIZE - NEXTSIZE;

    public static final int FREE_LIST_HEADER_SIZE = Long.SIZE / Byte.SIZE;

    public static final int LIST_HEADER_SIZE = Long.SIZE / Byte.SIZE;

    public static final int HEADER_SIZE = FREE_LIST_HEADER_SIZE + LIST_HEADER_SIZE;

    private RandomAccessFile f;

    /**
     * Constructor
     */
    public SupplierTable() throws Exception {

        f = new RandomAccessFile("suppliers.dat", "rw");

        // Clear the data in the file
        f.setLength(0);

        // Set the list header and free list header
        f.writeLong(-1);
        f.writeLong(-1);

        // Where is the file pointer?
        assert (f.getFilePointer() == HEADER_SIZE);
        assert (f.length() == HEADER_SIZE);

    }


    public static void main(String[] args) throws Exception {
        SupplierTable st = new SupplierTable();

        st.insert(1, "Smith", 20, "London");
        st.insert(0, "Jones", 10, "Paris");
        st.insert(3, "Blake", 30, "Paris");
        st.insert(2, "Clark", 20, "London");
        st.insert(5, "Adams", 30, "Athens");

        st.selectAll();

        st.delete(3);
        st.selectAll();
        st.delete(2);
        st.selectAll();
        st.insert(8, "Whites", 20, "Canton");
        st.selectAll();
        st.insert(22, "Harrys", 20, "Berlin");
        st.selectAll();
        st.delete(88);
        st.delete(1);
        st.delete(5);
        st.delete(22);
        st.delete(0);
        st.delete(8);
        st.selectAll();
    }


    /**
     * @param sno    The supplier number (key)
     * @param sname
     * @param status
     * @param city
     */
    public void insert(int sno, String sname, int status, String city) throws IOException {

        Long sno_pos = find(sno);

        if (sno_pos == -1) {
            // get the free list header
            f.seek(LIST_HEADER_SIZE);
            long freepos = f.readLong();

            // move file pointer to correct free
            // location
            if (freepos == -1) {
                freepos = f.length();
                f.seek(freepos);
            } else {
                f.seek(freepos);
                long nextfree = f.readLong();
                this.setFreeListHead(nextfree);
            }

            //seek back to the freepos determined
            f.seek(freepos);

            //write record
            f.writeInt(sno);
            f.writeChars(this.resize(sname, SNAMELEN));
            f.writeInt(status);
            f.writeChars(this.resize(city, SCITYLEN));
            f.writeLong(-1);

            //Print successful log
            System.out.println("INSERTED record with sno " + sno + " to memory location " + freepos);

            // insert into empty list
            long list = this.getListHead();
            if (list == -1) {
                this.setListHead(freepos);
                return;
            }

            // insert into non empty list
            long prev = -1;
            long curr = this.getListHead();

            // insert into front of list is special case?
            if (sno < this.getSno(curr)) {
                this.setListHead(freepos);
                f.seek(freepos + NEXTOFFSET);
                f.writeLong(curr);
                return;
            }

            // We are not inserting into the empty list or in to
            // the front of the list so we are inserting somewhere
            // in the middle or at the end.
            while (curr != -1 && sno > this.getSno(curr)) {
                int snocurr = this.getSno(curr);
                prev = curr;
                curr = this.getNext(curr);
            }

            //set previous pointer to current position
            this.setNext(prev, freepos);

            //set current pointer to the next position
            this.setNext(freepos, curr);


        } else {
            //print out warning when try to insert the same sno
            System.out.println("WARNING: INSERT FAILED, SNO " + sno + " has been added to the database already at memory location " + sno_pos);
        }

        return;
    }


    /**
     * Delete the record sno from the table.
     *
     * @param sno
     */
    public void delete(int sno) throws IOException {


        //*********************************************
        // Updating the list


        long snoPos = find(sno);
        // Traverse the list using the pointers
        long tempPos = getListHead();
        f.seek(tempPos);

        if (snoPos != -1) {         // If true, the sno is in the table to delete

            // Special Case: sno is first item in list
            if (tempPos == getListHead() && tempPos == snoPos) {
                long next = getNext(tempPos);
                setListHead(next);
            }

            while (tempPos != snoPos) {
                // Check what the pointer is for that row
                long prev = getNext(tempPos);
                long posToOverwrite = tempPos + NEXTOFFSET;

                // Is that pointer where sno is located?
                // If so, we need to update
                if (prev == snoPos) {          // If true, we are at a row that points to the row we want to delete
                    f.seek(prev);
                    long next = getNext(snoPos);        //can sub prev into snoPos b/c they are the same
                    f.seek(posToOverwrite);
                    f.writeLong(next);
                    // check to see if we are at the end of the file
                    if (next == -1)
                        break;
                    f.seek(next);
                    tempPos = snoPos;
                } else {
                    tempPos = getNext(tempPos);
                }

                // Check to make sure we aren't at the end of the file
                if (tempPos == -1) {
                    f.seek(tempPos);
                    break;
                }
            }

            //********************************************
            // Update the Free List

            long pos = getFreeListHead();
            // Special  Case: The Free List is empty
            if (pos == -1) {
                setFreeListHead(snoPos);
                f.seek(snoPos);
                f.writeLong(-1);
            }
            // The Free List is not empty
            else {
                while (pos != -1) {
                    long tmp = f.getFilePointer();
                    f.seek(pos);
                    pos = f.readLong();
                    if (pos == -1) {
                        // Use the free list as a stack
                        f.seek(snoPos);
                        f.writeLong(getFreeListHead());


                        setFreeListHead(snoPos);
                    }
                }
            }
            System.out.println("DELETING sno: " + sno + " at position " + snoPos);
        }
    }


    /**
     *  Prints out the table
     */
    public void selectAll() throws IOException {

        // have to start where the list head is pointing
        long pos = getListHead();
        // Special Case: The list is empty
        if (pos == -1) {
            System.out.println(" There is nothing in the file");
        } else {
            f.seek(pos);
            System.out.println("SNO" + "    " + "SNAME" + "   " + "STATUS" + "   " + "CITY");
            while (pos != -1) {

                // Get the sno
                long sno = f.readInt();
                pos += SNOSIZE;
                f.seek(pos);

                // Get the sname
                String sname = getString(pos);   // Make a method for this
                pos += SNAMESIZE;
                f.seek(pos);

                // Get the status
                long status = f.readInt();
                pos += STATUSSIZE;
                f.seek(pos);

                // Get the scity
                String city = getString(pos);
                pos += SCITYSIZE;
                f.seek(pos);

                System.out.println(sno + "   |  " + sname.trim() + " | " + status + "  |  " + city.trim());

                // Once we are here we have read through the values in a row
                // Now we need to move the file pointer to where next is pointing
                pos = f.readLong();
                if (pos == -1) {
                    return;
                }
                f.seek(pos);
            }
        }
    }


    //****************************************************************
    //***************     Helpers     ********************************
    //****************************************************************


    /**
     * @param s - The string to reformat
     *          Precondition: s.length() < l && s != null
     * @param l - the length of our desired string
     *          Precondition: l > 0
     * @return - s padded with char code 0
     */
    private static String resize(String s, int l) {
        String format = "%1$-" + l + "s";
        String tmp = String.format(format, s);
        return tmp;
    }

    /**
     *
     * @param sno - the desired supplier number
     * @return - the position of this number
     *          -1 if it is not in the file
     * @throws IOException
     */
    private long find(int sno) throws IOException {

        long temp = f.getFilePointer();
        long pos = getListHead();

        // Special Case: the list is empty
        if (getListHead() == -1) {
            return -1;
        }
        // The list is not empty
        while (pos != -1) {
            f.seek(pos);
            if (f.readInt() == sno) {
                //We have found the sno
                f.seek(temp);
                return pos;
            }
            pos = getNext(pos);
            if (pos != -1)
                f.seek(pos);
        }
        f.seek(temp);
        return -1;
    }


    //****************************************************************
    // Getters
    //****************************************************************


    /**
     *
     * @param pos - refers to the start of a record
     * @return
     */
    private int getSno(long pos) throws IOException {
        long tmp = f.getFilePointer();
        f.seek(pos);
        int sno = f.readInt();
        f.seek(tmp);
        return sno;
    }

    /**
     *
     * @param pos - should point at the start of a record
     * @return the next field of the record
     * @throws IOException
     */
    private long getNext(long pos) throws IOException {
        long tmp = f.getFilePointer();
        f.seek(pos + NEXTOFFSET);
        long retval = f.readLong();
        f.seek(tmp);
        return retval;
    }

    /**
     *
     * @return - the list head
     * @throws IOException
     */
    private long getListHead() throws IOException {
        long temp = f.getFilePointer();
        // List head is at 0 always
        f.seek(0);
        long returnVal = f.readLong();
        f.seek(temp);
        return returnVal;
    }

    /**
     *
     * @return - the free list head
     * @throws IOException
     */
    private long getFreeListHead() throws IOException {
        long temp = f.getFilePointer();
        f.seek(LIST_HEADER_SIZE);
        long returnVal = f.readLong();
        f.seek(temp);
        return returnVal;
    }

    /**
     *
     * @param pos - position of the string
     * @return - the String
     * @throws IOException
     */
    private String getString(long pos) throws IOException {

        long temp = f.getFilePointer();
        f.seek(pos);
        String returnString = "";
        // If we were to change the size of city and sname this loop would have to be changed
        for(long i = pos; i < pos+SNAMESIZE; i+= Character.SIZE/Byte.SIZE) {
            // f.seek(i);
            returnString += f.readChar();
        }
        f.seek(temp);
        return returnString;
    }

    //****************************************************************
    // Setters
    //****************************************************************


    /**
     *
     * @param val - position of the first free space
     *            val = -1 || val > HEADER_SIZE
     * side effect: wrote val at location LIST_HEADER_SIZE
     * @throws IOException
     */
    // Helper method to update the free list head
    private void setFreeListHead(long val) throws IOException {
        long tmp = f.getFilePointer();
        f.seek(LIST_HEADER_SIZE);       // seek to the free list head location
        f.writeLong(val);
        f.seek(tmp);
    }

    /**
     *
     * @param val - position of the first free space
     *            val = -1 || ...
     * side effect: wrote val at location LIST_HEADER_SIZE
     * @throws IOException
     */
    // Helper method to update the free list head
    private void setListHead(long val) throws IOException {
        long tmp = f.getFilePointer();
        f.seek(0);       // seek to the free list head location
        f.writeLong(val);
        f.seek(tmp);
    }

    /**
     * Set the next field given the position of a record.
     * @param pos - the position of the start of a record.
     * @param val - the value to set the next field too.
     * @throws IOException
     */
    private void setNext(long pos, long val) throws IOException {
        long tmp = f.getFilePointer();
        f.seek(pos + NEXTOFFSET);
        f.writeLong(val);
        f.seek(tmp);
    }


}
