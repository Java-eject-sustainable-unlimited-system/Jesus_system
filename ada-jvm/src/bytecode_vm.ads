with Ada.Streams;
with Interfaces;

package Bytecode_VM is
   Bytecode_Error : exception;

   function Execute
     (Code : Ada.Streams.Stream_Element_Array)
      return Interfaces.Integer_32;
end Bytecode_VM;
