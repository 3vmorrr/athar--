package org.athar.app.books;
import android.os.ParcelFileDescriptor;

interface IBookPdfRenderer {
    int open(String token, in ParcelFileDescriptor document);
    ParcelFileDescriptor render(String token, int page, int width);
    void close(String token);
}
