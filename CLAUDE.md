## Problem description

The file `data/9.5.0.610/user.config` contains an XML document.

In `configuration/userSetting/DxO.PhotoLab.Properties.Settings/setting/value`, there is XML of type 
`<ArrayOfanyType xmlns="http://schemas.microsoft.com/2003/10/Serialization/Arrays" xmlns:i="http://www.w3.org/2001/XMLSchema-instance">` 
embedded as a string.

The embedded XML is structured as follows:

```
<ArrayOfanyType ... >
    <anyType ...>
        <a:...>...</a:...>
        <a:...>...</a:...>
        ...
        <a:OutputName> ... </a:OutputName>
        ...
        <a:...>...</a:...>
        <a:...>...</a:...>
    </anyType>
    <anyType ...>
    ...    
    </anyType>
    ...
</ArrayOfanyType>
```

The `ArrayOfanyType` element contains a set of `anyType` elements representing a list of _export to disk options_ in 
DxO PhotoLab, each of which is a named set of properties coded as `a` elements, representing one such _option_. 
One of these properties, the `OutputName` property, is the _name_ of the respective _export to disk option_.

What is desired is a simple graphical application that reads the embedded XML, and displays the _names_ of the _export to disk
options_ in an editable form in a freely reorderable list. The application should also provide a way to remove an _option_. 
As soon as anything is changed, i.e. reordered, renamed or removed, a button to save changes should become clickable.

Clicking the button should create a backup of the existing `user.config` file with a timestamp appended to the file name 
and save the changed `ArrayOfanyType` in the `user.config` file, replacing the original one.

The application should also provide a way to restore saved `user.config` files.
